# ITT – Idempotent Task Triggers for Clojure

Sometimes you just have the need to trigger an idempotent task!

Idempotent tasks, in this context, are defined as functions with the
following properties:
* they have side-effects
* the return value doesn't matter
* they do asynchronous work
* if the environment / state doesn't change from the outside, only the
  first invocation in a series of invocations would change the state.

As this description might be a bit too abstract, please take a look at
the following examples:

## Example 1 – The Cleaner

Imagine you want to let your house cleaned by a cleaner.  You'll tell
the cleaner to clean your house.  The cleaner will start to clean your
house.  After a couple of hours, your house is cleaned.  The
side-effect here is that your house is cleaned afterwards, the dirt is
removed.  Now, when it's clean and if you don't place some dirt again
in your house, no subsequent cleaning will have an effect.  As there
is no dirt, the cleaner will probably just look into your rooms and
say that they are already cleaned.

## Example 2 – Asynchronous Sending Of Mails

Imagine that you have to send emails in your program.  It's often the
case that users of your software will get emails from your software.
Maybe you have an online-shop, where people can register accounts and
you want to validate the email-addresses by sending mails to them.
Maybe there are also other actions that cause the sending of mails to
individuals like placing orders.  Footnote: this is called
transactional emails in the current marketing-lingo.  Anyways, to
decouple your software, instead of sending the emails directly, you
store them in a queue for the sake of this example.  That way you can
decouple your code.  You'll have another function which takes these
emails from the queue periodically and sends them via smtp, or an
HTTP-wrapper around smtp.  The email is then removed from the queue.
As you see, there are side-effects again: a mail is actually sent, the
mail is removed from the queue.  It's also asynchronous, the time when
you know you'll have to send an email isn't the time when you really
have to send the email.  And also in this example, if your function
runs multiple times but the world (your queue), doesn't change from
the outside, only a first invocation will really send emails as the
queue is empty afterwards.  Note, that you can observe in this example
that the asynchronous function is not self-conflicting-free.  If you'd
invoke your function to send the mails from the queue twice in
parallel, you might end up with having sent your emails twice if you
don't undertake countermeasures.

## The Two Possible States Of Your Async Work

Now, we can conclude that there are only 2 possible states to consider
when having a situation like this.

### First State

Our idempotent task is not running at the moment.  We store an email
in our queue or put some dirt in our house.  We want to trigger our
idempotent task to get it fixed, so we will trigger it.  Eventually,
our mails will be sent / our house will be cleaned.

### Second State

Our idempotent task is running at the moment.  Again, we store an
email in our queue or put some dirt in our house, but the idempotent
task might be already over this point.

For the cleaner example: imagine you had an accident with your coffee
machine, but the cleaner already cleaned the kitchen. For the mailing
example: you are currently working off your mail queue, but another
customer just registered.

The solution here is easy, just run the idempotent task again, when
the current run is over and you'll know that it is fixed eventually –
when something like that doesn't happen during this second run again,
of course.

But it's also clear that it doesn't matter if we have some accident in
the living room, also as the cleaner would just start again to clean
the whole house.  Or if you're the mails guy: if two emails are stored
concurrently to the first run of your task, a single subsequent
invocation is enough, because all emails are processed by every single
invocation.  Otherwise your task wouldn't be idempotent.

## Usage

When you already extracted your code for your asynchronous work into a
synchronous function, you can just convert it to a trigger (with
`->trigger`) and use that instead of your function.

Have a look at this:

``` clojure
(defn clean-the-house [])
(def trigger-clean-the-house (->trigger clean-the-house))

(have-kitchen-accident) ;; now the kitchen is dirty
(trigger-clean-the-house) ;; it doesn't block here, only clean the house!

;;

(defn send-mails []
  (doseq [m (get-all-mails-from-database-queue)]
    (send-mail! m)
    (remove-from-database-queue m)))

(def trigger-send-mails (->trigger send-mails))
(store-customer-mail {:customer :a})
(trigger-send-mails) ;; eventually, customer :a will get the mail
```

Note that, of course you should only have one triggerified version of
your function. If you have multiple triggers of the same function and
that function is not self-conflicting-free, you'll get into trouble.

Although it might not matter in your specific case, please note, that
you loose the ability for a clean shutdown here.  If your project is
already doing some set-up and tear-down stuff, use
`->trigger-component`. This will return a map instead with three
functions under the keys `:trigger`, `:shutdown` and `:shutdown-now`.
For the teardown phase you can use either `shutdown` or `shutdown-now`
which map to the respective methods of the underlying
`ThreadPoolExecutor` object.

## Alternative Approaches Which You Can Replace With Triggers

Of course, there are other approaches to trigger such idempotent tasks.

A busy loop is one valid alternative, but for almost every use-case a
waste of resources.  Why not just trigger when you know that work has
to be done?

Another alternative is just creating futures.  When your task is not
self-conflicting-free, you could get into trouble when it runs in
parallel with itself.  Better design your task to be
self-conflicting-free, then.

And a third approach is to just submit your task to a single thread
pool executor.  Note that you have an unbounded queue by default.  If
you're doing something that could take a bit longer than expected,
you'll also get into problems, memory-wise.

## Implementation

The need for this came out from a couple of clojure (on the JVM)
projects.  Although a pure clojure-implementation would be really
cool, this library makes just use of the Java standard library.  The
implementation in clojure is just configuring a `ThreadPoolExecutor`
with a bounded `ArrayBlockingQueue` and that's already it.  Although
the implementation is thus really underwhelming (go, just copy-paste
it :-)), the concept and the tests might be more interesting.  As I
don't want to copy the code, nor the tests into every project, it's
now in the wild.

Note that all function calls caused by the trigger run in a single
thread.  This is because it is assumed that your function is not
thread-safe.  Your idempotent task has thus not to be conflict-free
with itself.

## Related Patterns

Although not investigated thoroughly, the Request Batch pattern
https://martinfowler.com/articles/patterns-of-distributed-systems/request-batch.html
seems to be (partially) implementable with triggers.
