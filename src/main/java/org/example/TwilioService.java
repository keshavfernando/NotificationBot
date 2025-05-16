package org.example;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

public class TwilioService {

    public static final String Account_SSID = "";
    public static final String Account_TOKEN = "";
    public static final String sendFromNum = "";
    public static final String sendToNum = "";


    // needs to be integrated with SQL DataBase

    public static void sendText(String content)
    {
        Twilio.init(Account_SSID, Account_TOKEN);
        Message message = Message.creator(new PhoneNumber(sendToNum), new PhoneNumber(sendFromNum), content).create();
        System.out.println("Message SID: " + message.getSid());
    }
}
