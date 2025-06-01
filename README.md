The **pps** repo is for Payment Processing System. This is implementation of Solution 2.

This solution perform fraud check with the FCS system(**fcs**). It uses an intermediary the BS Broker System (**bs**) to insulate both the system pps and fcs.

In this solution all interfaces between the PPS and BS is based on REST APIs and JSON.
And all interface between the BS and FCS is based on messaging and XML.
