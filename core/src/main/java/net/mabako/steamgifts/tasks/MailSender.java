package net.mabako.steamgifts.tasks;


import java.util.Calendar;
import java.util.Properties;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;

public class MailSender {
    private String toList;
    private String ccList;
    private String bccList;
    private String subject;
    private String from;

    private String txtBody;
    private String htmlBody;
    private String replyToList;
    private boolean authenticationRequired = false;

    private static String smtpServer;
    private static String user;
    private static String pass;

    public static void setCredentials(String smtpServer, String user, String pass) {
        MailSender.smtpServer = smtpServer;
        MailSender.user = user;
        MailSender.pass = pass;
    }

    public MailSender(String from, String toList, String subject, String txtBody, String htmlBody
    ) {
        this.txtBody = txtBody;
        this.htmlBody = htmlBody;
        this.subject = subject;
        this.from = from;
        this.toList = toList;
        this.ccList = null;
        this.bccList = null;
        this.replyToList = null;
        this.authenticationRequired = true;

    }


    public void sendAuthenticated() throws AddressException, MessagingException {
        authenticationRequired = true;
        send();
    }

    /**
     * Send an e-mail
     *
     * @throws MessagingException
     * @throws AddressException
     */
    public void send() throws AddressException, MessagingException {
        Properties props = new Properties();

        // set the host smtp address
        props.put("mail.smtp.host", smtpServer);
        props.put("mail.user", from);

        props.put("mail.smtp.starttls.enable", "true");  // needed for gmail
        props.put("mail.smtp.auth", "true"); // needed for gmail
        props.put("mail.smtp.port", "587");  // gmail smtp port

        /*Authenticator auth = new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication("mobile@mydomain.com", "mypassword");
            }
        };*/


        Session session;

        if (authenticationRequired) {
            Authenticator auth = new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(user, pass);
                }
            };
            props.put("mail.smtp.auth", "true");
            session = Session.getDefaultInstance(props, auth);
        } else {
            session = Session.getDefaultInstance(props, null);
        }

        // get the default session
        session.setDebug(true);

        // create message
        Message msg = new javax.mail.internet.MimeMessage(session);

        // set from and to address
        try {
            msg.setFrom(new InternetAddress(from, from));
            msg.setReplyTo(new InternetAddress[]{new InternetAddress(from, from)});
        } catch (Exception e) {
            msg.setFrom(new InternetAddress(from));
            msg.setReplyTo(new InternetAddress[]{new InternetAddress(from)});
        }

        // set send date
        msg.setSentDate(Calendar.getInstance().getTime());

        // parse the recipients TO address
        java.util.StringTokenizer st = new java.util.StringTokenizer(toList, ",");
        int numberOfRecipients = st.countTokens();

        javax.mail.internet.InternetAddress[] addressTo = new javax.mail.internet.InternetAddress[numberOfRecipients];

        int i = 0;
        while (st.hasMoreTokens()) {
            addressTo[i++] = new javax.mail.internet.InternetAddress(st
                    .nextToken());
        }
        msg.setRecipients(javax.mail.Message.RecipientType.TO, addressTo);

        // parse the replyTo addresses
        if (replyToList != null && !"".equals(replyToList)) {
            st = new java.util.StringTokenizer(replyToList, ",");
            int numberOfReplyTos = st.countTokens();
            javax.mail.internet.InternetAddress[] addressReplyTo = new javax.mail.internet.InternetAddress[numberOfReplyTos];
            i = 0;
            while (st.hasMoreTokens()) {
                addressReplyTo[i++] = new javax.mail.internet.InternetAddress(
                        st.nextToken());
            }
            msg.setReplyTo(addressReplyTo);
        }

        // parse the recipients CC address
        if (ccList != null && !"".equals(ccList)) {
            st = new java.util.StringTokenizer(ccList, ",");
            int numberOfCCRecipients = st.countTokens();

            javax.mail.internet.InternetAddress[] addressCC = new javax.mail.internet.InternetAddress[numberOfCCRecipients];

            i = 0;
            while (st.hasMoreTokens()) {
                addressCC[i++] = new javax.mail.internet.InternetAddress(st
                        .nextToken());
            }

            msg.setRecipients(javax.mail.Message.RecipientType.CC, addressCC);
        }

        // parse the recipients BCC address
        if (bccList != null && !"".equals(bccList)) {
            st = new java.util.StringTokenizer(bccList, ",");
            int numberOfBCCRecipients = st.countTokens();

            javax.mail.internet.InternetAddress[] addressBCC = new javax.mail.internet.InternetAddress[numberOfBCCRecipients];

            i = 0;
            while (st.hasMoreTokens()) {
                addressBCC[i++] = new javax.mail.internet.InternetAddress(st
                        .nextToken());
            }

            msg.setRecipients(javax.mail.Message.RecipientType.BCC, addressBCC);
        }

        // set header
        msg.addHeader("X-Mailer", "MyAppMailer");
        msg.addHeader("Precedence", "bulk");
        // setting the subject and content type
        msg.setSubject(subject);

        Multipart mp = new MimeMultipart("related");

        // set body message
        MimeBodyPart bodyMsg = new MimeBodyPart();
        bodyMsg.setText(txtBody, "iso-8859-1");

        bodyMsg.setContent(htmlBody, "text/html");
        mp.addBodyPart(bodyMsg);

        // set attachements if any

        msg.setContent(mp);

        // send it
        javax.mail.Transport.send(msg);
    }


}