package org.example;

import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.json.JSONObject;
import spark.Spark;

import java.awt.*;
import java.time.Instant;
import java.util.EnumSet;
import java.util.concurrent.CountDownLatch;
import java.awt.Color;


public class DiscordBot extends ListenerAdapter
{

    private static final String STRIPE_APIKEY = "";
    private static final String STRIPE_SECRETWEBHOOK = "";
    private static final String Discord_Channel_ID = "";
    private static JDA api;

    private static final CountDownLatch initLatch = new CountDownLatch(1);


    public static void main(String[] arguments) throws Exception
    {
        String token = "";

        try
        {
            api = JDABuilder.createDefault(token).enableIntents(EnumSet.allOf(GatewayIntent.class)).addEventListeners(new DiscordBot()).build();
            api.awaitReady();
            api.updateCommands().addCommands(Commands.slash("pingusers", "Message to be pinged").addOption(OptionType.STRING, "text", "Message to ping", true)).queue(success -> { System.out.println("Slash command has been registered");}, failure -> {System.out.println("Failed to register commands: " + failure.getMessage());
            failure.printStackTrace();});

            System.out.println("Bot is ready and command registration has been queued");

            try
            {
                initLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            initializeStripeWebhook(); //command to initialize webhook;
            System.out.println("Stripe webhook is up and running");
        }

        catch (Exception e)
        {
            System.err.println("Error with bot: ");
            e.printStackTrace();
        }

        System.out.println("Slash command '/pingusers' has been registered!");

    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event)
    {
        if (event.getName().equals("pingusers"))
        {
            String message = event.getOption("text").getAsString();

            TwilioService.sendText(message);

            event.reply("Ping sent to numbers").queue();
        }
    }

    private static void initializeStripeWebhook()
    {
        Stripe.apiKey = STRIPE_APIKEY;

        Spark.port(4567);

        Spark.post("/webhook" , (request, response) -> {
            String payload = request.body();
            String sigHeader = request.headers("Stripe-Signature");

            Event event;

            try
            {
                event = Webhook.constructEvent(payload, sigHeader, STRIPE_SECRETWEBHOOK);
            }
            catch (SignatureVerificationException e)
            {
                System.out.println("Invalid Stripe Signature");
                response.status(400);
                return "Invalid Signature";

            }

            System.out.println("Recieved Stripe event: " + event.getType());

            switch (event.getType())
            {
                case "payment_intent.payment_failed":
                    handlePaymentFailed(event);
                    break;
                case "invoice.payment_failed":
                    handleInvoicePaymentFailed(event);
                    break;
                case "charge.failed":
                    handleChargeFailed(event);
                    break;
            }

            response.status(200);
            return "Webhook has been processed";
                }
                );

        Spark.get("/health", (request, response) ->
        {
            response.status(200);
            return "Stripe webhook is still running";
        });

        System.out.println("Stripe webhook server started on port 4567");
        System.out.println("Webhook URL: http://domain/webhook"); //get domain to change
        System.out.println("Health check: http://your/health"); // change to domain
    }

    private static void handlePaymentFailed(Event event)
    {
        PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer().getObject().orElse(null);
        if (paymentIntent == null) return;

        String customerID = paymentIntent.getCustomer();
        String paymentIntentID = paymentIntent.getId();
        String failureMessage = paymentIntent.getLastPaymentError() != null ?
                paymentIntent.getLastPaymentError().getMessage() : "Unknown Error";
        double amount = paymentIntent.getAmount();
        String currency = paymentIntent.getCurrency().toUpperCase();

        sendDiscordNotification("Payment Failed", "A payment attempt has failed", "Customer ID: " + customerID + "\n" + "Payment ID: " + paymentIntentID + "\n" + "Amount: " + amount + " " + currency + "\n" + "Reason: " + failureMessage, Color.RED);

    }

    private static void handleChargeFailed(Event event)
    {
        JSONObject chargeObject = new JSONObject(event.getDataObjectDeserializer().getRawJson());
        JSONObject charge = chargeObject.getJSONObject("object");

        String customerID = charge.optString("customer", "Unknown");
        String chargeID = charge.optString("id", "Unknown");
        String failureMessage = charge.optString("failure_message", "Unknown");
        String failureCode = charge.optString("failure_code", "Unknown");
        double amount = charge.optDouble("amount", 0) / 100.0;
        String currency = charge.optString("currency", "usd").toUpperCase();

        sendDiscordNotification(
                "Charge Failed",
                "A charge attempt has failed",
                "Customer ID: " + customerID + "\n" + "Charge ID: " + chargeID + "\n" + "Amount: " + amount + " " + currency + "\n" + "Failure Code: " + failureCode + "\n" + "Reason: " + failureMessage , Color.RED);

    }

    private static void handleInvoicePaymentFailed(Event event)
    {
        JSONObject invoiceObject = new JSONObject(event.getDataObjectDeserializer().getRawJson());
        JSONObject invoice = invoiceObject.getJSONObject("object");

        String customerID = invoice.optString("customer", "Unknown");
        String invoiceID = invoice.optString("id", "Unknown");
        String failureMessage = invoice.has("last_payment_error") ? invoice.getJSONObject("last_payment_error").optString("message", "Unknown") : "Payment failed";
        double amount = invoice.optDouble("amount_due", 0) / 100.0;
        String currency = invoice.optString("currency", "usd").toUpperCase();

        sendDiscordNotification(
                "Invoice Payment Failed",
                "An invoice payment has failed",
                "Customer ID: " + customerID + "\n" +
                "Invoice ID: " + invoiceID + "\n" + "Amount: " + amount + " " + currency + "\n" + "Reason: " + failureMessage, Color.RED
        );


    }

    private static void sendDiscordNotification(String title, String description, String details, Color color)
    {
        try
        {
            TextChannel channel = api.getTextChannelById(Discord_Channel_ID);

            if (channel == null)
            {
                System.out.println("Could not find Discord channel with ID: " + Discord_Channel_ID);
            }

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(title)
                    .setDescription(description)
                    .addField("Details", details, false)
                    .setColor(color)
                    .setTimestamp(Instant.now());

            channel.sendMessageEmbeds(embed.build()).queue(
                    success -> System.out.println("Discord notification was sent"),
                    failure -> System.err.println("Failed to send Discord notification: " + failure.getMessage()));

        } catch (Exception e) {
            System.err.println("Error sending Discord notification");
            e.printStackTrace();
        }

    }
}
