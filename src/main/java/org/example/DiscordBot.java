package org.example;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;

public class DiscordBot extends ListenerAdapter
{

    public static void main(String[] arguments) throws Exception
    {
        String token = "Bot Token";
        JDA api = JDABuilder.createDefault(token).enableIntents(GatewayIntent.MESSAGE_CONTENT).enableIntents(GatewayIntent.GUILD_MESSAGES).addEventListeners(new DiscordBot()).build();

    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event)
    {
        if (event.getAuthor().isBot()) return;

        String messageContent = event.getMessage().getContentRaw();

        String notiMessage = messageContent.substring(0, 5);


        if (notiMessage.equals("!ping"))
        {
            String message = messageContent.substring(6);
            TwilioService.sendText(message);
            event.getChannel().sendMessage("Ping Sent: " + message).queue();
            System.out.println("Message sent: " + message);
        }
    }
}
