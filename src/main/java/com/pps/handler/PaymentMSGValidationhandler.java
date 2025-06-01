package com.pps.handler;

import java.util.Set;
import java.util.UUID;

import org.springframework.integration.core.GenericHandler;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pps.common.model.PaymentInfo;
import com.pps.utils.PPSConstants;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

public class PaymentMSGValidationhandler implements GenericHandler<Message<String>> {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	@Override
	public Object handle(Message<String> message, MessageHeaders headers) {
		PaymentInfo paymentInfo = null;

		try {

			objectMapper.findAndRegisterModules();

			paymentInfo = objectMapper.readValue(message.getPayload(), PaymentInfo.class);
			
			paymentInfo.setTransactionId(UUID.randomUUID());
			
			Set<ConstraintViolation<PaymentInfo>> violations = validator.validate(paymentInfo);
			if (!violations.isEmpty()) {
				throw new IllegalArgumentException("Validation failed: " + violations);
			}
			return MessageBuilder.withPayload(objectMapper.writeValueAsString(paymentInfo))
					.copyHeaders(headers)
					.build();
		} catch (Exception e) {
			return MessageBuilder.withPayload(message.getPayload())
					.copyHeaders(headers)
					.setHeader("validation.error", e.getMessage())
					.setHeader(PPSConstants.FAILED, true)
					.build();
		}
	}
}
