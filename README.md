The **pps** repo is for Payment Processing System. This is implementation of Soultion 2.

This soultion perfrom fraud check with the FCS system(**fcs**). It uses an intermidatray the BS Broker System (**bs**) to insulate both the system pps and fcs.

In this solution all interaces between the PPS and BS is based on REST APIs and JSON.
And all interface between the BS and FCS is based on messaging and XML.
