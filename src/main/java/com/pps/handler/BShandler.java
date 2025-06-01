package com.pps.handler;

import org.springframework.integration.core.GenericHandler;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pps.common.model.PaymentCanonical;
import com.pps.common.model.PaymentInfo;
import com.pps.common.utils.MessageConvertor;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BShandler implements GenericHandler<Message<String>> {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public Object handle(Message<String> message, MessageHeaders headers) {
		try {

			objectMapper.findAndRegisterModules();

			PaymentInfo paymentInfo = objectMapper.readValue(message.getPayload(), PaymentInfo.class);

			PaymentCanonical paymentCanonical = new PaymentCanonical();
			paymentCanonical.setPaymentInfo(paymentInfo);

			String paymentCanonicalJson = MessageConvertor.covertPojToJson(paymentCanonical);
			
			log.info("JSON Request to BS ->" + paymentCanonicalJson);
			
			return MessageBuilder.withPayload(paymentCanonicalJson)
					.copyHeaders(headers)
					.build();
		} catch (Exception e) {
			return MessageBuilder.withPayload(message.getPayload())
					.copyHeaders(headers)
					.setHeader("validation.error", e.getMessage())
					.setHeader("failed", true)
					.build();
		}
	}

}
