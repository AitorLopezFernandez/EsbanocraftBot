package Esbanocraft;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import java.awt.Color;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EsbanocraftBot extends ListenerAdapter {

    private final List<String> LINKS_PROHIBIDOS = Arrays.asList("discord.gg/", "http://", "https://", ".com", ".net");
    private final List<String> PALABRAS_NSFW = Arrays.asList("nopor", "gore", "sexo", "tetas", "teta", "pene", "pito", "vagina", "hentai", "xxx", "porn", "paja", "pajillero", "ereccion", "orgasmo", "clitoris", "ano", "recto", "coito", "follar", "follando");
    private final List<String> PALABRAS_OFENSIVAS = Arrays.asList("puto", "puta", "maricon", "gilipollas", "subnormal", "pendejo", "malnacido", "zorra", "estupido", "tonto", "idiota", "mierda");
    private final Map<Long, Integer> strikes = new HashMap<>();

    public static void main(String[] args) {
        String token = System.getenv("ESBANOCRAFTBOTTOKEN");
        
        try {
            JDA jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS)
                    .addEventListeners(new EsbanocraftBot())
                    .build();

            // Esperamos a que el bot conecte correctamente
            jda.awaitReady();
            
            // LIMPIEZA: Borra los comandos globales antiguos para que no se vean repetidos
            jda.updateCommands().queue();
            
            String serverdId = System.getenv("ESBANOCRAFT_GUILD_ID");
            // CAMBIO CRÍTICO: Registro directo en el servidor del cliente para que sea INSTANTÁNEO
            Guild guild = jda.getGuildById(serverdId);

            if (guild != null) {
                guild.updateCommands().addCommands(
                    Commands.slash("ban", "Banea a un usuario del servidor")
                        .addOption(OptionType.USER, "usuario", "El usuario a banear", true)
                        .addOption(OptionType.STRING, "motivo", "Razón del baneo", false),
                    
                    Commands.slash("kick", "Expulsa a un usuario del servidor")
                        .addOption(OptionType.USER, "usuario", "El usuario a expulsar", true)
                        .addOption(OptionType.STRING, "motivo", "Razón de la expulsión", false),
                    
                    Commands.slash("suspender", "Aplica un timeout de 10 minutos")
                        .addOption(OptionType.USER, "usuario", "El usuario a suspender", true)
                        .addOption(OptionType.STRING, "motivo", "Razón de la suspensión", false)
                ).queue(success -> System.out.println("✅ ¡COMANDOS ACTIVADOS PARA EL CLIENTE!"));
            } else {
                System.out.println("❌ No se encontró el servidor. Verifica el ID.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) return;

        String command = event.getName();
        Member target = event.getOption("usuario").getAsMember();
        String razon = event.getOption("motivo") != null ? event.getOption("motivo").getAsString() : "Incumplir normas";

        if (target == null) {
            event.reply("No se pudo encontrar al usuario.").setEphemeral(true).queue();
            return;
        }

        switch (command) {
            case "ban" -> {
                if (!event.getMember().hasPermission(Permission.BAN_MEMBERS)) {
                    event.reply("No tienes permiso para banear.").setEphemeral(true).queue();
                    return;
                }
                enviarEmbedYEjecutar(event, target, "BANEADO", razon, Color.RED);
            }
            case "kick" -> {
                if (!event.getMember().hasPermission(Permission.KICK_MEMBERS)) {
                    event.reply("No tienes permiso para expulsar.").setEphemeral(true).queue();
                    return;
                }
                enviarEmbedYEjecutar(event, target, "EXPULSADO", razon, Color.ORANGE);
            }
            case "suspender" -> {
                if (!event.getMember().hasPermission(Permission.MODERATE_MEMBERS)) {
                    event.reply("No tienes permiso para suspender.").setEphemeral(true).queue();
                    return;
                }
                enviarEmbedYEjecutar(event, target, "SUSPENDIDO", razon, Color.YELLOW);
            }
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || !event.isFromGuild()) return;

        String content = event.getMessage().getContentRaw().toLowerCase();

        if (LINKS_PROHIBIDOS.stream().anyMatch(content::contains) || 
            PALABRAS_NSFW.stream().anyMatch(content::contains) || PALABRAS_OFENSIVAS.stream().anyMatch(content::contains)) {
            
            event.getMessage().delete().queue();
            long userId = event.getAuthor().getIdLong();
            int contador = strikes.getOrDefault(userId, 0) + 1;
            strikes.put(userId, contador);

            if (contador >= 3) {
                strikes.remove(userId);
                ejecutarAccion(event.getMember(), "EXPULSADO", "Exceso de strikes (3/3)");
                event.getChannel().sendMessage("🚨 " + event.getAuthor().getAsMention() + " ha sido expulsado por acumular 3 strikes.").queue();
            } else {
                event.getChannel().sendMessage("⚠️ " + event.getAuthor().getAsMention() + 
                    ", ¡prohibido ese contenido! Strike: **" + contador + "/3**").queue();
            }
        }
    }

    private void enviarEmbedYEjecutar(SlashCommandInteractionEvent event, Member target, String tipo, String razon, Color color) {
        String emoji = tipo.equals("EXPULSADO") ? "🚨" : "⚠️";

        EmbedBuilder ebPublico = new EmbedBuilder()
                .setTitle(emoji + " Registro de Moderación " + emoji)
                .addField("Acción", tipo, true)
                .addField("Usuario", target.getAsMention(), true)
                .addField("Moderador", event.getUser().getAsMention(), true)
                .addField("Motivo", razon, false)
                .setThumbnail(target.getUser().getEffectiveAvatarUrl())
                .setColor(color)
                .setFooter("Esbanocraft Security System", event.getJDA().getSelfUser().getAvatarUrl());

        event.replyEmbeds(ebPublico.build()).queue();

        if (tipo.equals("EXPULSADO")) {
            target.getUser().openPrivateChannel().queue(ch -> {
                ch.sendMessage("Has sido expulsado de " + event.getGuild().getName() + "\nMotivo: " + razon).queue(
                    s -> ejecutarAccion(target, tipo, razon),
                    e -> ejecutarAccion(target, tipo, razon)
                );
            });
        } else {
            ejecutarAccion(target, tipo, razon);
        }
    }

    private void ejecutarAccion(Member target, String tipo, String razon) {
        try {
            switch (tipo) {
                case "BANEADO" -> target.ban(0, java.util.concurrent.TimeUnit.DAYS).reason(razon).queue();
                case "EXPULSADO" -> target.kick().reason(razon).queue();
                case "SUSPENDIDO" -> target.timeoutFor(Duration.ofMinutes(10)).reason(razon).queue();
            }
        } catch (Exception e) {
            System.out.println("Error al ejecutar: " + e.getMessage());
        }
    }
}