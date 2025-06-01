package com.pps.producer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * A JMS producer to send processed payment to PPS.
 * 
 * @author shashank
 *
 */
@Component
@Slf4j
public class JmsPPSProducer {

	@Autowired
	private JmsTemplate jmsTemplate;

	@Value("${active-mq.pps-req-queue}")
	private String ppsReqQueue;

	public void sendMessageTest(String message) {
		try {
			log.info("Attempting Send message to PPS on queue: " + ppsReqQueue);
			jmsTemplate.convertAndSend(ppsReqQueue, message);
		} catch (Exception e) {
			log.error("Recieved Exception during send Message: ", e);
		}
	}
}