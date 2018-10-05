Still continuing my trend of looking at Corda Services, I have some more tips to help your CorDapp work smoothly. This time around, we will focus on using <code>trackBy</code> to initiate Flows from inside a Service and the discrete problem that can arise if you are not careful.

This should be a relatively short post as I can lean upon the work in my previous posts: <a href="https://lankydanblog.com/2018/08/19/corda-services-101/" target="_blank" rel="noopener">Corda Services 101</a> and <a href="https://lankydanblog.com/2018/09/22/asynchronous-flow-invocations-with-corda-services/" target="_blank" rel="noopener">Asynchronous Flow invocations with Corda Services</a>. The content found in <a href="https://lankydanblog.com/2018/09/22/asynchronous-flow-invocations-with-corda-services/" target="_blank" rel="noopener">Asynchronous Flow invocations with Corda Services</a> is very relevant to this post and will contain extra information not included within this post.

This post is applicable to both Corda Open Source and Enterprise. The versions at the time of writing are Open Source <code>3.2</code> and Enterprise <code>3.1</code>.
<h3>A brief introduction to trackBy</h3>
<code>trackBy</code> allows you to write code that executes when a transaction containing states of a specified type completes. Whether they are included as inputs or outputs, the code will still trigger.

From here, you can decide what you want it to do. Maybe something very simple, like logging that a state has been received. Or, maybe something more interesting, such as initiating a new Flow. This use-case makes perfect sense for this feature. Once a node receives a new state or consumes one, they can start a new Flow that represents the next logical step in a workflow.

Furthermore, there are two versions of <code>trackBy</code>. One, the <code>trackBy</code> I keep mentioning, that can be used within a CorDapp. The other, <code>vaultTrackBy</code>, is called from outside of the node using RPC.

The problems presented in this post are only present in the CorDapp version, <code>trackBy</code>. Therefore, we will exclude <code>vaultTrackBy</code> for the remainder of this post.
<h3>What is this discrete problem?</h3>
Deadlock. When I word it that way, it isn't very discrete. But, the way it happens is rather subtle and requires a good understanding of what is going on to figure it out. As mentioned before, this issue is very similar to the one detailed in <a href="https://lankydanblog.com/2018/09/22/asynchronous-flow-invocations-with-corda-services/" target="_blank" rel="noopener">Asynchronous Flow invocations with Corda Services</a>. Furthermore, another shoutout to R3 for diagnosing this problem when I faced it in a project and I am sure they are going to iron this out. Until then, this post should save you some head scratching in case you run into the same problem.

I will quote what I wrote in my previous post as its explanation is only missing one point in regards to this post.

<em>"The Flow Worker queue looks after the order that Flows execute in and will fill and empty as Flows are added and completed. This queue is crucial in coordinating the execution of Flows within a node. It is also the source of pain when it comes to multi-threading Flows ourselves."</em>

<img class="alignnone size-full wp-image-4571" src="https://lankydanblog.files.wordpress.com/2018/09/corda-flow-queue.png" alt="Corda Flow Queue" width="1302" height="504" />

<em>"Why am I talking about this queue? Well, we need to be extra careful not to fill the queue up with Flows that cannot complete.</em>

<em>How can that happen? By starting a Flow within an executing Flow who then awaits its finish. This won't cause a problem until all the threads in the queue's thread pool encounter this situation. Once it does happen, it leaves the queue in deadlock. No Flows can finish, as they all rely on a number of queued Flows to complete."</em>

<img class="alignnone size-full wp-image-4572" src="https://lankydanblog.files.wordpress.com/2018/09/corda-flow-queue-deadlock.png" alt="Corda Flow Queue deadlock" width="1846" height="244" />

That marks the end of my copypasta. I am going to keep saying this though, really, I suggest you read through <a href="https://lankydanblog.com/2018/09/22/asynchronous-flow-invocations-with-corda-services/" target="_blank" rel="noopener">Asynchronous Flow invocations with Corda Services</a> for a thorough explanation into this subject.

What has this got to do with <code>trackBy</code>? Calling <code>trackBy</code> from a Service will run each observable event on a Flow Worker thread. In other words, each event takes up a spot on the queue. Starting a Flow from here will add another item to the queue and suspend the current thread until the Flow finishes. It will stay in the queue until that time. If you end up in a situation where all the spots on the queue are held by the observable events, rather than actual Flows, I got one word for you. Deadlock. It is the exact same situation I've detailed before but starting from a different epicenter.

On the bright side, the solution is a piece of cake (where did this saying come from anyway?).
<h3>The section where the problem is fixed</h3>
Now that you know what the problem is. Altering a "broken" version to one shielded from deadlock will only require a few extra lines.

Let's take a look at some code that is very similar to what lead me to step onto this landmine:

[gist https://gist.github.com/lankydan/e19936741301d0f160c33ecc657ef350 /]

This Service uses <code>trackBy</code> to start a new Flow whenever the node receives new <code>MessageState</code>s. For all the reasons mentioned previously, this code has the potential to deadlock. We don't know when, or if it will ever happen. But, it could. So we should probably sort it out before it is an issue.

The code below will do just that:

[gist https://gist.github.com/lankydan/fb850ffc5805182766b4dfbc5c4fe42e /]

I have added a few comments to make it clearer what changed since only a few lines were added.

All this change does, is start the Flow on a new thread. This then allows the current thread to end. Remember, this is important because this thread holds onto a position in the queue. Allowing it to end, frees up a slot for whatever comes next. Whether it is another observable event from <code>trackBy</code> or a Flow. It does not matter. As long as the thread is released, the possibility of deadlock occurring due to this code is naught.
<h3>Releasing you from this thread</h3>
Please take a moment to bask in the glory of the pun I made in this sections header. Maybe it's not that good, but I'm still proud of it.

In conclusion, using <code>trackBy</code> in a Corda Service is perfect for starting off new processes based on information being saved to the node. But, you need to be careful when starting new Flows from a <code>trackBy</code> observable. This is due to the observable holding onto a Flow Worker thread and therefore a spot in the queue. If your throughput reaches higher numbers, you risk the chance of your node deadlocking. You could end up in a situation where the queue is blocked by threads that are all waiting for a Flow to finish but with no actual Flows in the queue. By moving the Flow invocations onto a separate thread from the observable thread. You allow the once held spot on the queue to be released. There is now no chance of your <code>trackBy</code> code causing deadlock.

The code used in this post can be found on my <a href="https://github.com/lankydan/corda-service-trackby-flows" target="_blank" rel="noopener">GitHub</a>.

If you found this post helpful, you can follow me on Twitter at <a href="http://www.twitter.com/LankyDanDev" target="_blank" rel="noopener">@LankyDanDev</a> to keep up with my new posts.