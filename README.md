### Bitclouds-scala
This is a proof-of-concept demonstrating how it might be possible to allow `ssh` 
access to a server (or container) but only if the user has paid a lightning invoice.

The session will only be authenticated, and therefore any commands issued by the user
will only be executed once the server has confirmed payment has been made. Perhaps
interestingly, "hold invoices" (a feature of the lightning protocol) could also be used
which would allow a server-operator to decide whether to actually keep the payment or
simply refund/cancel it. Perhaps well-behaved users can simply use the servie for free
and only ones who are misbehaving (in the server operator's sole discretion) will actually
end up paying.

For example, let us say that a prospective user has already generated an `ssh` key pair
locally using `ssh-keygen`. This means that the user has key(s) in its `~/.ssh/` and that
when the user tries to `ssh <theserver>`, the user's ssh client will automatically try to
use the key(s) to authorize with the server. (note: if the user only wants to try a specific
key, it can use the `-i <keyfile>` to specify which key they would like to try with the server.

During this initial handshake the server sees the public key(s) of the user, and, even though
the server may never have seen those keys before, it can still consider granting access. The
server can use a `SSH_USERAUTH_BANNER` messsage to request payment. Until payment is received
the server simply does not authorize the user. Once required payment is received, the server
authorizes the user and everything proceeds as normal. This whole process is transparent to
the user in the sense that it is simply using its keys to login to the server -- the first login
simply may take a bit longer (since payment will be collected during that time). One future logins
the server could check the balance of the user and, if the remaining balance is adequate, grant
authorization immediately.

#### A note about this implementation.
This particular implementation of the concept is purely a demonstration. In fact no payment is actually
collected. Right now the server simply emulates the process, but it should be relatively straight
forward to connect it up to an actual payment mechanism such as lightning.
