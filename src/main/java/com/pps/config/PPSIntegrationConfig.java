package com.pps.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.config.EnableIntegrationManagement;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.Pollers;
import org.springframework.integration.http.dsl.Http;
import org.springframework.integration.jms.dsl.Jms;
import org.springframework.integration.scheduling.PollerMetadata;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.scheduling.support.PeriodicTrigger;

import com.pps.common.model.PaymentAudit;
import com.pps.common.model.PaymentCanonical;
import com.pps.common.utils.MessageConvertor;
import com.pps.common.utils.PaymentConstants;
import com.pps.handler.BShandler;
import com.pps.handler.PaymentMSGValidationhandler;
import com.pps.utils.PPSConstants;

import jakarta.jms.ConnectionFactory;
import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableIntegration
@EnableIntegrationManagement
@Slf4j
public class PPSIntegrationConfig {

	@Value("${active-mq.pps-req-queue}")
	public String ppsReqQueue;

	@Value("${active-mq.pps-dlq-queue}")
	public String ppsDlqQueue;

	@Value("${rest.bs-post-url}")
	public String bsPostURL;

	@Value("${rest.bs-get-url}")
	public String bsGetURL;

	// --- Channels ---
	@Bean
	public MessageChannel validatedChannel() {
		return new DirectChannel();
	}

	@Bean
	public MessageChannel bsChannel() {
		return new DirectChannel();
	}
	
	@Bean
	public MessageChannel bsPostChannel() {
		return new DirectChannel();
	}

	// --- Step 1: Read JSON from JMS queue and validate ---
	@Bean
	public IntegrationFlow inputFlow(ConnectionFactory connectionFactory, JmsTemplate jmsTemplate) {

		return IntegrationFlow
				.from(Jms.messageDrivenChannelAdapter(connectionFactory)
						.destination(ppsReqQueue))
				.<Message<String>>handle(new PaymentMSGValidationhandler())
				.route(Message.class, message -> message.getHeaders().containsKey(PPSConstants.FAILED),
						mapping-> mapping
						.subFlowMapping(true, sf-> sf.handle(msg-> {
							jmsTemplate.convertAndSend(ppsDlqQueue, msg.getPayload());
						}))
						.subFlowMapping(false, sf -> sf.channel(validatedChannel()))
						)
				.get();
	}

	// --- Step 2: Send to processing queue (validatedJsonQueue) ---
	@Bean
	public IntegrationFlow sendToProcessingQueue(ConnectionFactory connectionFactory, JmsTemplate jmsTemplate) {
		return IntegrationFlow.from(validatedChannel())
				.handle(new BShandler()).handle(Http.outboundGateway(bsPostURL)
						.httpMethod(HttpMethod.POST).expectedResponseType(String.class).errorHandler(errorHandler -> {
							if (errorHandler.getStatusCode().is2xxSuccessful()) {
								log.error("Error code - > " + errorHandler.getStatusCode().value());
								return false;
							} else {
								return true;
							}
						}))
				.channel(bsPostChannel())
				.get();
	}

	// --- Step 3: Listen for reply on responseQueue ---
	@Bean
	public IntegrationFlow responseListener(ConnectionFactory connectionFactory) {
		AtomicBoolean shouldTerminate = new AtomicBoolean();
		shouldTerminate.set(true);
		List<Message<String>> paymentCanonicalJson = new ArrayList<>();

		return IntegrationFlow
				.from(bsPostChannel())
				.gateway(IntegrationFlow.fromSupplier(() -> {

					// Condition to terminate the loop, e.g., check for a specific value or count
					if (shouldTerminate(shouldTerminate)) {
						return null; // Returning null terminates the flow
					}
					return paymentCanonicalJson.get(0);
				}, e -> e.poller(poller()))
						.handle(Http.outboundGateway(bsGetURL + "/{id}")
								.httpMethod(org.springframework.http.HttpMethod.GET)
								.uriVariable("id", (Message<String> t)-> {
									paymentCanonicalJson.add(t);
									String paymentId =  getPaymentId(t);
									return paymentId;
								})
								.expectedResponseType(String.class)
								.errorHandler(errorHandler -> {
									log.info("Error code - > " + errorHandler.getStatusCode().value() );
									if(errorHandler.getStatusCode().is2xxSuccessful()) {
										shouldTerminate.set(true);
										return false;
									}else {
										shouldTerminate.set(false);
										return true;
									}
								}))
						.handle(message -> {
							// Process the API response
							log.info("API Response: " + message.getPayload());

							PaymentCanonical paymentCanonical = MessageConvertor.covertJsonToPaymentCanonicalPojo(message.getPayload().toString());

							List<PaymentAudit> paymentAuditsList = paymentCanonical.getPaymentAudits();

							for (Iterator<PaymentAudit> iterator = paymentAuditsList.iterator(); iterator.hasNext();) {
								PaymentAudit paymentAudit = iterator.next();
								log.info("Status Code --> : " + paymentAudit.getStatusCode());
								if (PaymentConstants.FCS001.equalsIgnoreCase(paymentAudit.getStatusCode())
										|| PaymentConstants.FCS002.equalsIgnoreCase(paymentAudit.getStatusCode())) {
									log.info("--->>>processing Completed<<<---Processing is completed for transaction id for  - > {}", paymentCanonical.getPaymentInfo().getTransactionId());
									shouldTerminate.set(true);
									break;
								} else {
									log.info("Processing is continue for transaction id for  - > {}", paymentCanonical.getPaymentInfo().getTransactionId());
									shouldTerminate.set(false);
								}
							}
						})
						.get())
				.get();
	}
	
	private String getPaymentId(Message<String> paymentCanonicalJson) {
		if(paymentCanonicalJson != null) {
			log.info("API Payload: " + paymentCanonicalJson.getPayload());
			PaymentCanonical paymentCanonical = MessageConvertor.covertJsonToPaymentCanonicalPojo(paymentCanonicalJson.getPayload());
			return paymentCanonical.getPaymentInfo().getTransactionId().toString();
		} else {
			return null;
		}
	}

	private boolean shouldTerminate(AtomicBoolean shouldTerminate) {
        // Implement your logic to determine when to terminate the loop
        // Example: check a counter or a specific condition
        return shouldTerminate.get();
    }
	
	@Bean(name = PollerMetadata.DEFAULT_POLLER_METADATA_BEAN_NAME)
    public PollerMetadata poller() {
        return Pollers.trigger(new PeriodicTrigger(Duration.ofSeconds(5))).maxMessagesPerPoll(1).getObject();
    }
}