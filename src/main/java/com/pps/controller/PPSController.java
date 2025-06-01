package com.pps.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.pps.producer.JmsPPSProducer;

/**
 * A rest controller to facilitate the unit testing.
 * @author shashank
 *
 */
@RestController
public class PPSController {
	
	@Autowired
	private JmsPPSProducer jmsBSProducer;
	
	@PostMapping("/sendPaymentPPS")
	public void sendToTopic(@RequestBody String paymentCanonical) {
		jmsBSProducer.sendMessageTest(paymentCanonical);
	}
}
