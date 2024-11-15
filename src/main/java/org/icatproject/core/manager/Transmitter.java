package org.icatproject.core.manager;

import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import jakarta.jms.TextMessage;
import jakarta.jms.Topic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class Transmitter {

	private static final Logger logger = LoggerFactory.getLogger(Transmitter.class);

	@Resource
	ConnectionFactory connectionFactory;

	@Resource(lookup="jms/ICAT/log")
	Topic topic;

	public void processMessage(String operation, String ip, String body, long startMillis) {
		try (JMSContext context = connectionFactory.createContext(JMSContext.AUTO_ACKNOWLEDGE)) {
			TextMessage jmsg = context.createTextMessage(body);
			jmsg.setStringProperty("operation", operation);
			jmsg.setStringProperty("ip", ip);
			jmsg.setLongProperty("millis", System.currentTimeMillis() - startMillis);
			jmsg.setLongProperty("start", startMillis);

			context.createProducer().send(topic, jmsg);
			logger.debug("Sent jms message " + operation + " " + ip);
		} catch (JMSException e) {
			logger.error("Failed to send jms message " + operation + " " + ip);
		}
	}
}
