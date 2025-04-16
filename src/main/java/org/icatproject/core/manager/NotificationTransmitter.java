package org.icatproject.core.manager;

import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import jakarta.jms.ObjectMessage;
import jakarta.jms.Topic;

import org.icatproject.core.manager.NotificationMessage.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class NotificationTransmitter {

	private static final Logger logger = LoggerFactory.getLogger(NotificationTransmitter.class);

	@Resource
	ConnectionFactory connectionFactory;

	@Resource(lookup="jms/ICAT/Topic")
	Topic topic;

	public void processMessage(NotificationMessage notificationMessage) throws JMSException {
		if (connectionFactory == null) {
			return;
		}

		Message message = notificationMessage.getMessage();
		if (message != null) {
			try (JMSContext context = connectionFactory.createContext(JMSContext.AUTO_ACKNOWLEDGE)) {
				ObjectMessage jmsg = context.createObjectMessage();
				jmsg.setStringProperty("entity", message.getEntityName());
				jmsg.setStringProperty("operation", message.getOperation());
				jmsg.setObject(message.getEntityId());

				context.createProducer().send(topic, jmsg);
				logger.debug("Sent jms notification message " + message.getOperation() + " " + message.getEntityName() + " " + message.getEntityId());
			} catch (JMSException e) {
				logger.error("Failed to send jms notification message ");
			}
		}
	}
}
