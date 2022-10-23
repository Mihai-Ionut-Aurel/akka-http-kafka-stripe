# Akka HTTP Stripe webhook microservice example
This project implementation is based on [this microservice example](https://github.com/theiterators/akka-http-microservice/)

This project demonstrates the [Akka HTTP](https://doc.akka.io/docs/akka-http/current/?language=scala) library 
and Scala to write REST (micro)service that can receive [Stripe webhooks](https://stripe.com/docs/webhooks).
The app will write the incoming webhook to a different partition based on the event type.
Also, it will write failed events to Kafka which will help in identifying sources of error. At the same time,
capturing the errors will make sure you do not miss any important data.


Start the service with sbt:

```
$ sbt
> ~reStart
```

With the service up, you can forward your webhook events to your localhost using [Stripe CLI](https://stripe.com/docs/webhooks/test)
```
stripe listen --forward-to localhost:9000/stripe_webhooks
```
Next, you cna trigger some events using the command:
```
stripe trigger payment_intent.succeeded
```

### Testing

Execute tests using `test` command:

```
$ sbt
> test
```

## Author & license

If you have any questions regarding this project, contact:

Ionut Mihai <mihai.ionut.aurel@gmail.com>

For licensing info see LICENSE file in project's root directory.
