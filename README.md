# Custom Thread Pool

## Project Overview

This project implements a custom thread pool in Java without using `ThreadPoolExecutor`.

The goal of the project is to simulate the behavior of a configurable server-side task execution system that can process tasks concurrently, distribute them between worker threads, handle overload situations, and support both graceful and immediate shutdown.

The implementation was developed as part of a course project and demonstrates several important concurrency and system design concepts:

- custom worker management
- bounded task queues
- task distribution strategy
- rejection policy
- thread factory customization
- graceful and immediate shutdown
- support for both `Runnable` and `Callable` tasks
- detailed console logging

The pool implements the following custom interface:

- `execute(Runnable command)`
- `<T> Future<T> submit(Callable<T> callable)`
- `shutdown()`
- `shutdownNow()`

This makes it possible to use the pool both for fire-and-forget tasks and for tasks that return results through `Future`.

---

## Requirements Covered

This project covers the main requirements of the task:

- configurable pool parameters:
    - `corePoolSize`
    - `maxPoolSize`
    - `keepAliveTime`
    - `timeUnit`
    - `queueSize`
    - `minSpareThreads`
- custom overload handling through a rejection policy
- task distribution between multiple worker queues
- custom `ThreadFactory` with readable thread names
- worker termination after idle timeout
- graceful shutdown and immediate shutdown
- support for both `execute()` and `submit()`
- detailed logging of important lifecycle events
- demonstration program instead of unit tests 
- built-in runtime metrics used for performance analysis, configuration comparison, and observation of queue pressure, worker growth, task completion, and rejection rate

---

## Design Decisions

Several design decisions were made during the implementation.

### 1. One worker = one personal queue

Each worker owns its own bounded `BlockingQueue<Runnable>`.

This approach matches the task statement, where task distribution between several queues is explicitly allowed. It also makes the architecture easier to understand: a worker only processes tasks from its own queue.

### 2. `queueSize` is interpreted as the size of one worker queue

The task statement does not strictly define whether `queueSize` refers to one global queue or multiple per-worker queues.

In this implementation, `queueSize` means the capacity of **one worker queue**.

This decision is consistent with the chosen architecture of one queue per worker.

### 3. `minSpareThreads` means the minimum number of idle workers

The pool tries to keep at least a configured number of idle workers available.

If the number of free workers becomes too low and the pool has not yet reached `maxPoolSize`, it may create new workers in advance.

This helps reduce latency when new tasks arrive.

### 4. The balancing strategy is replaceable

Task distribution is not hardcoded directly inside the pool.

Instead, the pool uses a separate `TaskBalancer` abstraction.  
This makes the design more flexible and allows switching to another balancing strategy later.

The current implementation uses `RoundRobinBalancer` as the default strategy.

### 5. Workers implement `Runnable`, not `Thread`

A worker represents task-processing logic, not the thread object itself.

Actual Java threads are created by a custom `ThreadFactory`.  
This separation makes the design cleaner and closer to common concurrency best practices.

### 6. `submit()` is implemented through `FutureTask`

To support `Callable<T>` and `Future<T>`, submitted tasks are wrapped into `FutureTask`.

This allows the pool to reuse the same internal task execution pipeline that is already used for `Runnable` tasks.

### 7. Console logging is used instead of a full logging framework

For simplicity and educational clarity, the project uses `System.out.println(...)` for logging (the same as in the task statement).

This is enough to observe the lifecycle of the pool and task execution flow, even though a production-ready system would normally use a real logging framework.

### 8. `shutdown()` and `shutdownNow()` have different semantics

- `shutdown()` stops accepting new tasks but allows already accepted tasks to finish
- `shutdownNow()` stops accepting new tasks, clears pending queues, and interrupts worker threads

This behavior is close to the semantics of standard Java executors.

---

## Architecture

The implementation is based on a small set of clearly separated components.

At a high level, the architecture looks like this:

- `CustomThreadPool` is the main coordination class
- `Worker` objects execute tasks from their personal queues
- `CustomThreadFactory` creates named worker threads
- `TaskBalancer` selects the queue for a new task
- `RejectionPolicy` defines what happens during overload
- `PoolConfig` stores all pool parameters

The pool starts with `corePoolSize` workers.  
When task load increases, it may create additional workers up to `maxPoolSize`.

Each worker waits for tasks using `poll(keepAliveTime, timeUnit)`.  
If no task arrives within the timeout period, the worker may terminate, but only if the current number of workers is greater than `corePoolSize`.

This allows the pool to grow under load and shrink back when the extra workers are no longer needed.

The architecture was also designed with testability in mind.  
Key responsibilities were separated into small components such as configuration, balancing, rejection policy, worker control, and pool coordination, which made it possible to cover both deterministic logic and multithreaded behavior with unit and concurrency-oriented tests.

### Code structure
    src
    ├── main
    │   └── java
    │      └── org
    │         └── example
    │            ├── threadpool
    │            │   ├── api
    │            │   │   └── CustomExecutor.java
    │            │   ├── balancer
    │            │   │   ├── RoundRobinBalancer.java
    │            │   │   └── TaskBalancer.java
    │            │   ├── config
    │            │   │   └── PoolConfig.java
    │            │   ├── core
    │            │   │   └── CustomThreadPool.java
    │            │   ├── demo
    │            │   │   ├── DemoCallableTask.java
    │            │   │   ├── DemoTask.java
    │            │   │   └── Main.java
    │            │   ├── factory
    │            │   │   └── CustomThreadFactory.java
    │            │   ├── metrics
    │            │   │   └── PoolMetricsSnapshot.java
    │            │   ├── rejection
    │            │   │   └── RejectionPolicy.java
    │            │   └── worker
    │            │       └── WorkerController.java
    │            └── Main.java
    └── test
        └── java
           └── org
              └── example
                 └── threadpool
                    ├── balancer
                    │   └── RoundRobinBalancerTest.java
                    ├── config
                    │   └── PoolConfigTest.java
                    ├── core
                    │   ├── CustomThreadPoolConcurrentSubmissionTest.java
                    │   ├── CustomThreadPoolCoreWorkerIdleTimeoutTest.java
                    │   ├── CustomThreadPoolExecuteTest.java
                    │   ├── CustomThreadPoolGracefulShutdownTest.java
                    │   ├── CustomThreadPoolGrowthTest.java
                    │   ├── CustomThreadPoolIdleTimeoutTest.java
                    │   ├── CustomThreadPoolMaxPoolSizeLimitTest.java
                    │   ├── CustomThreadPoolMetricsTest.java
                    │   ├── CustomThreadPoolReuseAfterShrinkTest.java
                    │   ├── CustomThreadPoolShutdownNowMetricsTest.java
                    │   ├── CustomThreadPoolShutdownNowQueueClearBehaviorTest.java
                    │   ├── CustomThreadPoolShutdownTest.java
                    │   └── CustomThreadPoolSubmitTest.java
                    ├── factory
                    │   └── CustomThreadFactoryTest.java
                    └── rejection
                        └── RejectPolicyTest.java

- `CustomExecutor` — public interface of the custom thread pool API.
- `PoolConfig` — stores and validates all pool configuration parameters.
- `CustomThreadPool` — the main coordination class that manages workers, task submission, shutdown, and metrics.
- `Worker` — processes tasks from its personal queue and handles idle-timeout termination.
- `WorkerController` — internal contract used by workers to query pool state and report termination.
- `CustomThreadFactory` — creates named worker threads and logs their creation.
- `TaskBalancer` — abstraction for task distribution across worker queues.
- `RoundRobinBalancer` — default balancing strategy that distributes tasks in circular order.
- `RejectionPolicy` — abstraction for overload handling.
- `RejectPolicy` — rejection strategy that throws `RejectedExecutionException` when a task cannot be accepted.
- `PoolMetricsSnapshot` — immutable snapshot of runtime pool metrics.
- `DemoTask` — simple `Runnable` task used for demonstration scenarios.
- `DemoCallableTask` — simple `Callable` task used to demonstrate `submit()` and `Future`.
- `Main` — runs demonstration scenarios for normal execution, shutdown, and configuration comparison.

Test files will be discussed in the Test Coverage overview. 

---

## Main Components

### `CustomExecutor`

This is the public interface of the custom thread pool.  
It defines the following methods:

- `execute(Runnable command)`
- `<T> Future<T> submit(Callable<T> callable)`
- `shutdown()`
- `shutdownNow()`

This interface makes the pool easy to use from application code and keeps the public contract clear.

### `PoolConfig`

`PoolConfig` stores all configuration parameters of the pool in one place.

It contains:

- `corePoolSize`
- `maxPoolSize`
- `keepAliveTime`
- `timeUnit`
- `queueSize`
- `minSpareThreads`

The class also validates input values to prevent invalid pool configuration, such as:

- `corePoolSize < 1`
- `maxPoolSize < corePoolSize`
- negative timeout values
- invalid queue size
- invalid spare thread count

This makes pool initialization safer and easier to understand.

### `CustomThreadPool`

`CustomThreadPool` is the central coordination class of the project.

Its responsibilities include:

- storing pool configuration
- creating core workers during startup
- creating additional workers when needed
- distributing tasks between worker queues
- applying rejection policy when the pool is overloaded
- tracking worker lifecycle
- handling graceful and immediate shutdown

This class implements both:

- `CustomExecutor` — as the public API of the pool
- `WorkerController` — as the internal controller used by workers

This design keeps worker logic simpler while still allowing workers to query pool state.

### `Worker`

A `Worker` is a task-processing component that implements `Runnable`.

Each worker:

- owns a personal bounded queue
- waits for tasks from that queue
- executes accepted tasks
- tracks whether it is currently busy
- terminates on idle timeout if the pool allows it
- reports termination back to the pool

A worker is not the same as a Java `Thread`.  
It only contains execution logic. The actual thread object is created separately by `CustomThreadFactory`.

### `WorkerController`

`WorkerController` is a small internal interface used by `Worker`.

It allows a worker to:

- check whether the pool is shutting down
- check whether immediate shutdown was requested
- ask whether it may stop after idle timeout
- notify the pool when it terminates

This avoids hard-coding the worker to the full implementation of the pool and improves separation of responsibilities.

### `CustomThreadFactory`

`CustomThreadFactory` creates worker threads with readable and unique names.

Example thread names:

- `MyPool-worker-1`
- `MyPool-worker-2`

This class also logs thread creation events, which makes debugging and demonstration easier.

### `TaskBalancer`

`TaskBalancer` is an abstraction for queue selection.

Instead of hardcoding the distribution logic directly inside the pool, the pool asks the balancer which queue index should be used as a starting point.

This makes the architecture more flexible and allows replacing the strategy later if needed.

### `RoundRobinBalancer`

`RoundRobinBalancer` is the current implementation of `TaskBalancer`.

It distributes tasks in circular order:

- queue 0
- queue 1
- queue 2
- queue 0 again
- and so on

The implementation uses `AtomicInteger` to ensure thread-safe index updates.

### `RejectionPolicy`

`RejectionPolicy` defines what happens when the pool cannot accept a new task.

This is necessary when:

- all worker queues are full
- all workers are busy
- the pool cannot create more workers because it already reached `maxPoolSize`

Separating this logic into its own interface makes the overload handling strategy replaceable.

### `RejectPolicy`

`RejectPolicy` is the default overload handling strategy used in this project.

When a task cannot be accepted, this policy:

1. logs the rejection
2. throws `RejectedExecutionException`

This approach is straightforward, explicit, and easy to demonstrate.

---

## Task Distribution Strategy

The project uses a multi-queue design: each worker owns its own bounded queue.

When a new task is submitted, the pool does not place it into one global queue.  
Instead, it tries to choose an appropriate worker queue.

The current implementation uses the following approach:

1. the `TaskBalancer` selects a starting queue index
2. the pool performs a circular scan over worker queues
3. it tries to place the task into the first queue that has free capacity
4. if all existing queues are full, the pool may create a new worker
5. if the pool has already reached `maxPoolSize`, the rejection policy is applied

### Why Round Robin was chosen

Round Robin was chosen because it is:

- simple to implement
- easy to explain
- predictable in behavior
- fully sufficient for this course work 

It also matches the task statement, where Round Robin is explicitly suggested as one possible balancing algorithm.

### Why the final behavior is slightly smarter than pure Round Robin

The pool does not stop after checking only one queue.

Instead, the balancer provides a **starting point**, and then the pool checks other queues in circular order if needed.

This improves practical behavior:

- if the first selected queue is full, another queue may still accept the task
- task rejection is postponed until all existing queues are checked
- the system remains simple but behaves more robustly

### Possible future alternatives

Because the balancing strategy is isolated behind the `TaskBalancer` interface, we can choose another strategy such as:

- Least Loaded
- Shortest Queue First
- Random
- Power of Two Choices

These strategies were not implemented in the current version.

---

## Rejection Policy

The project uses a dedicated overload handling abstraction: `RejectionPolicy`.

The current implementation uses `RejectPolicy`.

### How it works

If the pool cannot accept a task after checking all available options, the task is rejected.

This happens when:

- the pool is already shut down
- all worker queues are full
- the number of workers already reached `maxPoolSize`

In this situation, `RejectPolicy`:

- logs a rejection message
- throws `RejectedExecutionException`

### Why this policy was chosen

This policy was chosen because it is:

- easy to implement
- easy to understand
- predictable
- useful for demonstration purposes

It also clearly shows overload situations during testing and makes failures visible immediately.

### Advantages

- simple and explicit behavior
- no hidden fallback logic
- easy to debug
- easy to explain in this course work (task) 

### Disadvantages

- rejected tasks are lost
- no automatic backpressure is applied
- callers must handle rejection explicitly if they want graceful degradation

### Possible alternative

A possible alternative would be `CallerRunsPolicy`, where the rejected task is executed in the calling thread.

This would reduce task loss and create natural backpressure, but it would also make the behavior less predictable and slightly more difficult to explain for this course work.

---

## Worker Lifecycle

Each worker is created by the pool and then executed inside a dedicated Java thread.

The lifecycle of a worker can be described as follows.

### 1. Creation

When the pool starts, it immediately creates `corePoolSize` workers.

Later, if load increases and the number of idle workers becomes too small, the pool may create additional workers up to `maxPoolSize`.

Each worker receives:

- a reference to the internal pool controller
- its personal bounded queue
- `keepAliveTime`
- `timeUnit`

### 2. Waiting for tasks

A worker waits for tasks using:

- `poll(keepAliveTime, timeUnit)`

This is an important design choice.

The worker does not use `take()`, because `take()` would block forever and would not allow the worker to detect idle timeout.

Using `poll(...)` allows the worker to wake up periodically and decide whether it should stop.

### 3. Task execution

When a task is received, the worker:

1. marks itself as busy
2. logs task execution start
3. executes `task.run()`
4. logs task completion
5. marks itself as idle again

If a task throws `RuntimeException`, the worker catches it, logs the failure, and continues processing other tasks.

This is important because one broken task must not kill the whole worker.

### 4. Idle timeout

If the worker does not receive a task during the configured timeout period, it asks the pool whether it is allowed to stop.

The pool allows idle termination only when the current number of workers is greater than `corePoolSize`.

This means:

- core workers stay alive
- extra workers may disappear when load becomes lower

This behavior allows the pool to scale up and later shrink back.

### 5. Graceful shutdown behavior

During `shutdown()`:

- new tasks are not accepted
- already queued tasks may still be processed

A worker finishes only when:

- graceful shutdown was requested
- and its personal queue becomes empty

This ensures that accepted tasks are not lost during normal shutdown.

### 6. Immediate shutdown behavior

During `shutdownNow()`:

- pending tasks are removed from worker queues
- worker threads are interrupted

If a worker is blocked while waiting for a task, interruption wakes it up.

If a task is already running, interruption is only a signal.  
Whether the task stops immediately depends on whether that task reacts correctly to interruption.

This behavior is consistent with normal Java threading semantics.

### 7. Termination

When a worker finishes, it:

- marks itself as no longer running
- notifies the pool about termination
- logs the termination event

The pool then removes the worker from its internal collections.

---

## Shutdown Behavior

The project supports two shutdown modes:

- `shutdown()`
- `shutdownNow()`

These two methods have different goals and different behavior.

### `shutdown()`

`shutdown()` starts a **graceful shutdown**.

After this method is called:

- the pool stops accepting new tasks
- tasks that were already accepted may still be processed
- workers continue running until their personal queues become empty
- after queues are empty, workers terminate normally

This mode is appropriate when the system should stop cleanly without losing accepted work.

### `shutdownNow()`

`shutdownNow()` starts an **immediate shutdown**.

After this method is called:

- the pool stops accepting new tasks
- pending tasks are removed from worker queues
- worker threads are interrupted

This mode is useful when the system must stop as quickly as possible.

### Important note about interruption

`shutdownNow()` interrupts worker threads, but interruption in Java is cooperative.

This means:

- if a worker is sleeping or waiting in an interruptible operation, it will usually stop quickly
- if a task ignores interruption, it may continue running for some time

Because of this, `shutdownNow()` should be understood as a **best-effort immediate shutdown**, not as an absolute guarantee that all running tasks stop instantly.

### Difference between accepted and running tasks

It is important to distinguish between:

- **pending tasks** — tasks still waiting in queues
- **running tasks** — tasks already being executed by workers

During `shutdownNow()`:

- pending tasks are cleared from queues
- running tasks are not forcibly removed, because they are already outside the queue

The only mechanism used for running tasks is thread interruption.

---

## Logging

The project includes detailed console logging for all major lifecycle events.

In this course work for simplicity, logging is implemented using `System.out.println(...)`.

A production-ready system would normally use a real logging framework such as:

- SLF4J
- Logback
- Log4j
- `java.util.logging`

However, for this course work, console logging is sufficient and makes behavior easy to observe.

### Logged events

The implementation logs the following events:

#### Thread creation

When a new worker thread is created:

    [ThreadFactory] Creating new thread: MyPool-worker-1

#### Task acceptance

When a task is successfully placed into a queue:

    [Pool] Task accepted into queue #1: DemoTask{...}
or:

    [Pool] Task accepted into newly created queue #3: DemoTask{...}

#### Task rejection

When the pool cannot accept a task:

    [Rejected] Task DemoTask{...} was rejected due to overload!

#### Task execution

When a worker starts and finishes task execution:

     [Worker] MyPool-worker-2 executes DemoTask{...}
     [Worker] MyPool-worker-2 finished DemoTask{...}

#### Task failure

If a task throws an exception:

     [Worker] MyPool-worker-2 task failed: DemoTask{...}, reason: ...

#### Idle timeout

When a worker has been idle for too long and stops:

     [Worker] MyPool-worker-2 idle timeout, stopping.

#### Worker termination

When a worker thread finishes:

     [Worker] MyPool-worker-2 terminated.

#### Shutdown events

When graceful or immediate shutdown is requested:

     [Pool] Graceful shutdown was requested.
     [Pool] Immediate shutdown was requested.
     [Pool] Cleared 3 pending task(s) from worker queues.

### Why this logging is useful

The logs help observe:

- worker creation and termination
- task flow through the system
- overload situations
- queue behavior
- shutdown behavior
- interruption during immediate shutdown

This makes the project easier to debug, explain, and demonstrate.

### Metrics summary output

In addition to event-based logs, the demo program prints a metrics summary after each scenario.

This summary includes:

- submitted tasks
- accepted tasks
- rejected tasks
- completed tasks
- current worker count
- peak worker count
- current and peak pending tasks
- scenario duration
- accepted throughput
- completed throughput
- rejection rate

This makes the demo not only illustrative, but also measurable.

---

## Demo Scenarios

The project includes a demonstration class `Main` that shows the behavior of the custom thread pool in several scenarios.

In addition to normal logs, each scenario prints a **metrics summary** at the end.  
This summary includes task counters, worker statistics, queue pressure, total duration, throughput, and rejection rate.

### Demo 1: `execute()` + overload + `shutdown()`

This scenario demonstrates:

- normal execution of `Runnable` tasks
- queue filling
- pool growth up to `maxPoolSize`
- graceful shutdown
- metrics collection for accepted, rejected, and completed tasks

In the measured run:

- all 12 submitted tasks were accepted
- all 12 accepted tasks were completed
- the pool scaled up to 4 workers
- peak pending task count reached 8
- rejection rate was 0%

This scenario shows that the default configuration can absorb this workload without task loss.

### Demo 2: `submit()` + `Future`

This scenario demonstrates:

- submission of `Callable<String>` tasks
- wrapping into `FutureTask`
- receiving results through `Future`
- blocking behavior of `Future.get()`
- metrics collection for accepted and completed tasks

In the measured run:

- all 3 submitted tasks were accepted
- all 3 tasks completed successfully
- peak worker count remained 2
- rejection rate was 0%

This scenario confirms that the pool correctly supports tasks with results.

### Demo 3: `shutdownNow()`

This scenario demonstrates:

- immediate shutdown request
- clearing of pending tasks from worker queues
- interruption of worker threads
- interruption-aware task behavior
- difference between accepted and completed tasks

In the measured run:

- all 8 submitted tasks were accepted
- only 3 tasks were completed
- 5 pending tasks were cleared from worker queues during `shutdownNow()`
- peak worker count reached 3
- rejection rate was 0%

This scenario clearly shows the difference between **accepted** work and **completed** work during immediate shutdown.

### Demo 4: configuration comparison

This scenario compares the behavior of the same workload under three different pool configurations.

The same load was submitted to all three configurations:

- 20 tasks
- each task duration = 2000 ms
- graceful shutdown after submission

#### Config A (small)
- `corePoolSize = 1`
- `maxPoolSize = 2`
- `queueSize = 1`
- `minSpareThreads = 0`

Measured result:

- submitted: 20
- accepted: 4
- rejected: 16
- completed: 4
- peak workers: 2
- rejection rate: 80%

#### Config B (medium)
- `corePoolSize = 2`
- `maxPoolSize = 4`
- `queueSize = 2`
- `minSpareThreads = 1`

Measured result:

- submitted: 20
- accepted: 12
- rejected: 8
- completed: 12
- peak workers: 4
- rejection rate: 40%

#### Config C (large)
- `corePoolSize = 3`
- `maxPoolSize = 6`
- `queueSize = 4`
- `minSpareThreads = 1`

Measured result:

- submitted: 20
- accepted: 20
- rejected: 0
- completed at snapshot time: 17
- peak workers: 5
- rejection rate: 0%

This configuration comparison demonstrates how pool sizing and queue capacity directly affect acceptance rate, rejection rate, queue pressure, and throughput.

---

## Test Coverage Overview

The project includes a set of unit and concurrency-oriented tests that validate the main parts of the custom thread pool implementation.

The test suite was designed step by step, starting from simple deterministic components and then moving to more complex multithreaded scenarios.

The tests were used not only to validate the implementation but also to improve it. In particular, one of the concurrency-oriented tests revealed a race condition in the idle-timeout logic, where multiple workers could stop at the same time and shrink the pool below `corePoolSize`. After identifying this issue through testing, the implementation was corrected and the fix was verified by rerunning the test suite.

### What is covered by tests

#### 1. Configuration validation

`PoolConfigTest` verifies that:

- valid configuration is created correctly
- invalid values are rejected
- constructor validation works as expected

This includes checks for:

- invalid `corePoolSize`
- invalid `maxPoolSize`
- negative `keepAliveTime`
- `null` `timeUnit`
- invalid `queueSize`
- invalid `minSpareThreads`

#### 2. Balancer behavior

`RoundRobinBalancerTest` verifies that:

- queue indexes are returned in circular order
- single-queue behavior is correct
- invalid queue count values are rejected

This confirms the correctness of the default balancing strategy.

#### 3. Rejection policy

`RejectPolicyTest` verifies that:

- rejected tasks cause `RejectedExecutionException`

This confirms that overload handling works according to the selected rejection strategy.

#### 4. Thread factory behavior

`CustomThreadFactoryTest` verifies that:

- invalid pool names are rejected
- created thread names are correct
- thread names are unique and increment properly

This ensures readable and deterministic worker thread naming.

#### 5. Task submission with results

`CustomThreadPoolSubmitTest` verifies that:

- `submit(Callable<T>)` returns a valid `Future`
- `Future.get()` returns the expected result
- multiple submitted tasks are executed correctly

This confirms correct support for `Callable` tasks and `Future`-based result handling.

#### 6. Runnable execution

`CustomThreadPoolExecuteTest` verifies that:

- `execute(Runnable)` really executes submitted tasks
- multiple `Runnable` tasks can be completed successfully

This confirms the basic execution behavior of the pool.

#### 7. Shutdown contract

`CustomThreadPoolShutdownTest` verifies that:

- `execute()` rejects new tasks after `shutdown()`
- `execute()` rejects new tasks after `shutdownNow()`
- `submit()` rejects new tasks after `shutdown()`
- `submit()` rejects new tasks after `shutdownNow()`

This confirms that shutdown state prevents acceptance of new work.

#### 8. Graceful shutdown semantics

`CustomThreadPoolGracefulShutdownTest` verifies that:

- tasks accepted before `shutdown()` are still allowed to complete
- queued tasks are not lost during graceful shutdown
- new tasks submitted after shutdown are rejected

This confirms the intended semantics of graceful shutdown.

#### 9. Runtime metrics

`CustomThreadPoolMetricsTest` verifies that:

- successful execution updates submitted / accepted / completed counters correctly
- rejected submissions update submitted / rejected counters correctly

This confirms that internal pool metrics remain consistent in simple scenarios.

#### 10. Immediate shutdown metrics behavior

`CustomThreadPoolShutdownNowMetricsTest` verifies that:

- during `shutdownNow()`, accepted tasks may be greater than completed tasks
- queued tasks may be removed before execution
- pending queue count becomes zero after immediate shutdown

This confirms the difference between accepted work and completed work during forced shutdown.

#### 11. Pool growth behavior

`CustomThreadPoolGrowthTest` verifies that:

- the pool can grow above `corePoolSize` when capacity is exhausted

`CustomThreadPoolMaxPoolSizeLimitTest` verifies that:

- the pool does not grow beyond `maxPoolSize`
- extra tasks are rejected when all workers and queues are already full

Together, these tests confirm correct worker growth behavior and correct upper-capacity limits.

#### 12. Idle timeout behavior

`CustomThreadPoolIdleTimeoutTest` verifies that:

- extra workers created under load can stop after being idle longer than `keepAliveTime`
- the pool shrinks back to `corePoolSize`

`CustomThreadPoolCoreWorkerIdleTimeoutTest` verifies that:

- core workers do not stop because of idle timeout

These tests are especially important because they validate one of the most concurrency-sensitive parts of the implementation.

#### 13. Reuse after shrink-back

`CustomThreadPoolReuseAfterShrinkTest` verifies that:

- after growing above `corePoolSize`
- and then shrinking back after idle timeout
- the pool still remains usable and can execute new tasks correctly

This confirms that worker lifecycle transitions do not break later task execution.

#### 14. Queue clearing behavior during `shutdownNow()`

`CustomThreadPoolShutdownNowQueueClearBehaviorTest` verifies that:

- a queued task is not executed if `shutdownNow()` clears the queue before that task starts

This is a behavioral confirmation of the immediate-shutdown semantics.

#### 15. Concurrent submission consistency

`CustomThreadPoolConcurrentSubmissionTest` verifies that under concurrent task submission:

- `submittedTaskCount = acceptedTaskCount + rejectedTaskCount`
- after graceful shutdown, `completedTaskCount = acceptedTaskCount`
- externally observed rejections match internal pool metrics
- executed task count matches completed task count

This test is useful for detecting race conditions and inconsistencies in counters and submission handling.

### Why these tests are important

This project is highly concurrent, which means that some errors cannot be found by simple functional checks alone.

Several tests were intentionally written around:

- queue pressure
- worker growth and shrink
- graceful shutdown
- immediate shutdown
- metric consistency
- concurrent task submission

This helped reveal and fix subtle multithreading issues, including race conditions in idle-timeout worker termination logic.

### Scope of testing

The goal of the test suite is not to prove that the implementation is perfect in all possible concurrency situations.

Instead, the goal is to provide strong coverage for:

- public API behavior
- configuration validation
- task acceptance and rejection
- worker lifecycle
- shutdown semantics
- runtime metrics
- important concurrency edge cases

For an educational custom thread pool project, this level of testing provides solid confidence in the correctness of the core behavior.

---

## Performance Notes

This project was implemented primarily as an educational custom thread pool (as course work statement defines), not as a production-ready high-performance replacement for `ThreadPoolExecutor`.

At the same time, the current version already includes **built-in runtime metrics**, which makes it possible to observe real pool behavior during demo execution instead of relying only on theoretical assumptions.

A full performance study could be extended later with dedicated benchmarks, repeated runs, and averaged results, but even the current implementation already provides useful measurable data.

### Collected runtime metrics

The pool collects the following internal metrics:

- `submittedTaskCount` — total number of tasks submitted to the pool
- `acceptedTaskCount` — total number of tasks accepted by worker queues
- `rejectedTaskCount` — total number of tasks rejected by the pool
- `completedTaskCount` — total number of tasks whose execution finished
- `currentWorkerCount` — current number of workers in the pool
- `busyWorkerCount` — number of workers currently executing tasks
- `idleWorkerCount` — number of currently idle workers
- `peakWorkerCount` — maximum number of workers observed during pool lifetime
- `currentPendingTaskCount` — total number of tasks currently waiting in worker queues
- `peakPendingTaskCount` — maximum queue pressure observed during pool lifetime

At the demo level, the application also measures:

- total scenario duration
- accepted throughput (`acceptedTaskCount / duration`)
- completed throughput (`completedTaskCount / duration`)
- rejection rate (`rejectedTaskCount / submittedTaskCount`)

### Why these metrics are useful

These metrics help evaluate several important aspects of pool behavior:

- how aggressively the pool scales up
- how often overload happens
- how many tasks are actually completed
- how much queue pressure builds up
- how shutdown mode affects accepted and completed work
- how configuration changes influence throughput and rejection

### Interpreting the metrics

Some metrics should be interpreted carefully:

- `submittedTaskCount` includes both accepted and rejected tasks
- `acceptedTaskCount` includes tasks that were successfully placed into queues
- `completedTaskCount` includes only tasks that actually finished execution
- during `shutdownNow()`, it is normal for `acceptedTaskCount` to be greater than `completedTaskCount`, because some accepted tasks may still be removed from queues before execution starts
- if a metrics snapshot is taken before all workers fully terminate, `currentWorkerCount` and `completedTaskCount` may still reflect in-progress work

This last point is visible in the measured data for the large configuration in Demo 4: the snapshot was taken before the final tasks and workers had fully finished, so the summary still showed active workers and incomplete task completion at that moment.

### Factors that influence performance in this implementation

The main factors affecting performance are:

- `corePoolSize`
- `maxPoolSize`
- `queueSize`
- `minSpareThreads`
- `keepAliveTime`
- task duration
- task arrival rate

### Expected effect of configuration parameters

#### `corePoolSize`

`corePoolSize` defines how many workers are created immediately and kept as the base pool size.

- if `corePoolSize` is too small, early task processing may be delayed
- if `corePoolSize` is too large, too many idle threads may be kept alive unnecessarily

For stable, constantly loaded systems, a larger `corePoolSize` may reduce latency.  
For bursty workloads, a moderate value is usually more reasonable.

#### `maxPoolSize`

`maxPoolSize` defines how much the pool may grow under pressure.

- if `maxPoolSize` is too small, overload and rejection will happen earlier
- if `maxPoolSize` is too large, the system may create too many threads and waste CPU time on scheduling and context switching

A higher `maxPoolSize` may improve throughput for blocking or waiting tasks, but too many threads can reduce efficiency.

#### `queueSize`

In this project, `queueSize` is the capacity of one worker queue.

- small queues make overload visible earlier and force the pool to grow faster
- large queues reduce rejection frequency but may increase waiting time inside queues

If queues are too large, tasks may spend more time waiting before execution.  
If queues are too small, the pool may reject tasks too aggressively.

#### `minSpareThreads`

`minSpareThreads` controls how many idle workers the pool tries to keep available.

- a higher value may reduce delay for newly arriving tasks
- a lower value may save resources when the system is mostly idle

If this value is too high, the pool may create workers earlier than necessary.  
If it is too low, sudden spikes may experience additional latency.

#### `keepAliveTime`

`keepAliveTime` controls how quickly extra workers disappear when load drops.

- a short timeout reduces resource usage faster
- a long timeout keeps extra workers alive longer and may help with repeated short spikes

If the timeout is too short, the pool may shrink too aggressively and recreate workers often.  
If it is too long, unnecessary workers may remain alive longer than needed.

### Practical observations

For the current design, the most balanced configuration is usually one where:

- `corePoolSize` covers normal load
- `maxPoolSize` allows moderate burst handling
- `queueSize` is large enough to absorb short spikes but not so large that tasks wait too long
- `minSpareThreads` is small but non-zero
- `keepAliveTime` is long enough to avoid frequent worker creation and destruction

### Suggested mini-study approach

A simple mini-study for this project can compare several configurations such as:

#### Configuration A
- small `corePoolSize`
- small queues
- low `maxPoolSize`

Expected result:
- early rejection under load
- lower memory usage
- lower idle overhead

#### Configuration B
- moderate `corePoolSize`
- moderate queues
- moderate `maxPoolSize`

Expected result:
- balanced behavior
- fewer rejections
- reasonable resource usage

#### Configuration C
- high `corePoolSize`
- large queues
- high `maxPoolSize`

Expected result:
- fewer rejections
- higher memory and thread management overhead
- potentially more waiting inside queues

For an educational report, it is acceptable to describe these expected trade-offs even if full benchmarking is not performed. In this project, the comparison was supplemented with actual measured runtime metrics from demo scenarios.

### Example measured results

#### Main demo scenarios
| Scenario | Submitted | Accepted | Rejected | Completed | Current Workers | Peak Workers | Peak Pending | Duration (s) | Accepted Throughput (tasks/s) | Completed Throughput (tasks/s) | Rejection Rate |
|----------|-----------|----------|----------|-----------|-----------------|--------------|--------------|--------------|-------------------------------|--------------------------------|----------------|
| Demo 1: execute() + overload + shutdown() | 12 | 12 | 0 | 12 | 0 | 4 | 8 | 17.031 | 0.705 | 0.705 | 0.00% |
| Demo 2: submit() + Future | 3 | 3 | 0 | 3 | 1 | 2 | 2 | 6.031 | 0.497 | 0.497 | 0.00% |
| Demo 3: shutdownNow() | 8 | 8 | 0 | 3 | 0 | 3 | 6 | 7.027 | 1.138 | 0.427 | 0.00% |


#### Configuration comparison results
| Configuration | corePoolSize | maxPoolSize | queueSize | minSpareThreads | Submitted | Accepted | Rejected | Completed | Current Workers | Peak Workers | Peak Pending | Duration (s) | Accepted Throughput (tasks/s) | Completed Throughput (tasks/s) | Rejection Rate |
|---------------|--------------|-------------|-----------|-----------------|-----------|----------|----------|-----------|-----------------|--------------|--------------|--------------|-------------------------------|--------------------------------|----------------|
| Config A (small) | 1 | 2 | 1 | 0 | 20 | 4 | 16 | 4 | 0 | 2 | 2 | 10.003 | 0.400 | 0.400 | 80.00% |
| Config B (medium) | 2 | 4 | 2 | 1 | 20 | 12 | 8 | 12 | 0 | 4 | 8 | 10.015 | 1.198 | 1.198 | 40.00% |
| Config C (large) | 3 | 6 | 4 | 1 | 20 | 20 | 0 | 17 | 3 | 5 | 16 | 10.014 | 1.997 | 1.698 | 0.00% |

### Observations from measured results

#### Observations from the main demo scenarios

- In Demo 1, the default configuration successfully handled all 12 submitted tasks without rejections and scaled up to 4 workers.
- In Demo 2, the pool correctly processed all `Callable` tasks and returned all `Future` results with no rejection.
- In Demo 2, the metrics snapshot was taken slightly before the last worker fully terminated, which is why `currentWorkerCount = 1` in the summary even though the remaining worker stopped immediately afterward.
- In Demo 3, immediate shutdown clearly changed the outcome: all 8 tasks were accepted, but only 3 were completed because the remaining pending tasks were removed from queues during `shutdownNow()`.
- Demo 3 also shows that accepted throughput and completed throughput may differ significantly when immediate shutdown interrupts normal execution.

#### Observations from the configuration comparison

- The comparison shows a strong dependency between pool capacity and rejection rate.
- Config A (small) rejected 80% of submitted tasks, which shows that a very small pool and very small queues quickly become overloaded.
- Config B (medium) provided a more balanced result: it accepted 12 of 20 tasks and rejected 40%.
- Config C (large) accepted all 20 submitted tasks and reached the highest accepted throughput.
- Config C also produced the highest peak queue pressure (`peakPendingTaskCount = 16`), which shows that larger configurations can buffer much more work before rejecting tasks.
- In Config C, the metrics snapshot was taken before the last tasks and workers had fully completed, which is why the summary still shows `completedTaskCount = 17` and `currentWorkerCount = 3` even though the remaining tasks finished shortly after.
- Overall, the measured data confirms that larger values of `corePoolSize`, `maxPoolSize`, and `queueSize` improve acceptance rate and throughput, but also allow more work to accumulate inside the pool.

### Suggested interpretation of the measured study

The measured results support the following general conclusion:

- smaller configurations fail earlier under burst load
- medium configurations provide a compromise between resource usage and acceptance rate
- larger configurations minimize rejection and maximize throughput, but may accumulate much larger pending queues

This makes configuration tuning an important part of thread pool design.

### Performance analysis summary

Compared with a purely theoretical discussion, the measured metrics make it possible to connect configuration directly to runtime behavior.

The collected results show that:

- graceful shutdown preserves accepted work and allows full completion of already queued tasks
- immediate shutdown may leave a significant gap between accepted and completed work
- queue size and maximum worker count strongly affect overload resistance
- larger configurations increase throughput and acceptance capacity
- smaller configurations are more resource-efficient, but reject burst traffic much earlier

For an educational custom thread pool, this level of performance analysis is sufficient to demonstrate both the implementation logic and the practical effect of configuration choices.

---

## Possible Improvements

The current implementation is intentionally simplified.  
Many improvements could be added later.

### 1. Add `awaitTermination()`

At the moment, the demo uses `Thread.sleep(...)` to wait for shutdown progress.

A more complete implementation could add an `awaitTermination()` method similar to standard Java executors.

### 2. Add more rejection policies

Currently the pool uses only `RejectPolicy`.

Possible alternatives:

- `CallerRunsPolicy`
- `DiscardPolicy`
- `DiscardOldestPolicy`
- custom backpressure strategies

### 3. Add more balancing strategies

The architecture already supports replacing the task balancer.

Possible future strategies:

- Least Loaded
- Shortest Queue First
- Random
- Power of Two Choices

### 4. Introduce a dedicated queue abstraction

At the moment, worker queues are represented directly by `ArrayBlockingQueue<Runnable>` inside `Worker`.

A future version could introduce a separate queue wrapper that would provide:

- queue identifiers
- queue-level logging
- queue statistics
- explicit queue state management

### 5. Replace console logging with a real logging framework

A future version could use:

- SLF4J
- Logback
- Log4j

This would allow:

- log levels
- timestamps
- file logging
- structured output

### 6. Improve synchronization and scalability

The current version uses relatively simple synchronization and collections in order to remain understandable.

A more advanced implementation could improve throughput and reduce contention by:

- refining lock granularity
- reducing synchronized sections
- using more specialized concurrent data structures

### 7. Extend metrics and monitoring

The current implementation already provides built-in runtime metrics, including:

- submitted task count
- accepted task count
- rejected task count
- completed task count
- current worker count
- busy and idle worker count
- peak worker count
- current and peak pending task count

A more advanced version could extend this monitoring support with additional capabilities such as:

- average task wait time in queue
- average task execution time
- separate counters for interrupted tasks
- per-worker queue statistics
- metrics history over time
- periodic metrics snapshots
- export of metrics for external monitoring tools


### 8. Extend API compatibility

The current API is intentionally small.

A more complete implementation could support more of the standard `ExecutorService` behavior, including richer shutdown and task management methods.

### 9. Add automated benchmarking

The current project already provides basic runtime metrics collected during demo scenarios.

A future version could extend this with repeatable benchmark runs, for example:

- fixed task counts
- multiple configurations
- repeated runs per configuration
- averaged throughput and rejection rate
- structured export of benchmark results

This would make the performance analysis more rigorous and easier to compare across configurations.

---

## Conclusion

This project implements a custom configurable thread pool in Java and demonstrates the core ideas behind concurrent task execution systems.

The implementation supports:

- configurable pool sizing
- bounded worker queues
- replaceable task balancing
- overload handling with rejection policy
- custom thread creation
- graceful shutdown
- immediate shutdown
- `Runnable` task execution
- `Callable` task submission with `Future`
- detailed lifecycle logging
- built-in runtime metrics

Although the implementation is simpler than standard library executors or production-grade server pools, it successfully demonstrates the main mechanisms required by the task (course work).

The most important result of the project is not maximum performance, but a clear understanding of:

- how worker threads are managed
- how tasks are queued and distributed
- how overload is handled
- how shutdown modes differ
- how configuration affects runtime behavior

The measured demo scenarios also show that configuration has a direct and visible effect on:

- acceptance rate
- rejection rate
- throughput
- worker scaling
- queue pressure

As an educational implementation, this project fulfills the assignment goals and provides a good foundation for further experiments with concurrency, configuration tuning, and custom executor design.