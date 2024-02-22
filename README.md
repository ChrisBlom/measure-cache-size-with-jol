# Table of Contents

1. [Optmizing memory footprint of caches with with JOL](#orgd70b69d)
    1. [The demo project](#orgb95b1d8)
    2. [Measuring the memory footprint of a cache:](#orga87c31d)
    3. [Optimizing memory usage](#org1a1e770)
    4. [Measuring the impact of optimizations:](#orge54cdc5)
    5. [Background reading:](#org5a887f9)
    6. [Consulting](#org187eaaa)

<a id="orgd70b69d"></a>

### The demo project

I've created a small demo project based on an actual project I did for a client some time ago.
It features a basic java project that shows how to measure the memory footprint of a cache and some techniques to reduce
this footprint.

# Optmizing memory footprint of caches with with JOL

Lots of applications use some type of in-memory storage.
A very common use case is caching results gathered from an external source, like a database or another service.
Another use case is loading static resources from disk when the application starts.

To prevent your application from running out of memory it is a good idea to know what the memory footprint of this
storage.

A common scenario is that you need to to cache N entries to ensure good cache utilization, and you need to determine how
much memory this takes.
Another one is were you have some fixed amound of memory, for example the VM you budgeted has 1024 MiB available, and
you want to
determine how large the cache can be.

To answer these question you need to determine the total memory footprint of the cache.
This is simple in theory, but somewhat tricky in practice: you could just walk the object graph and sum all the fields.
However the exact size of objects is hard to determine, each object requires some overhead which may vary per JVM
implementation and can even depend on the used JVM options.

Rather then depending on rough estimates, it is best to measure how much memory is used.
There is a tool called jol which traverses an object graph and sums the sizes, which gives
you an accurate measurement of the memory footprint.

<a id="orgb95b1d8"></a>

### Measuring the memory footprint of a cache:

In the example project I setup a mock cache, which is populated with generated data.
Just pretend the entries come from a database or some external service.

To measure the total memory usage of an object and all it references you can use jol's GraphLayout.totalSize():

    System.out.println(GraphLayout.parseInstance(cache).totalSize() / 1024d / 1024d + " MiB");

This will report the total size in MiB (mebibyte), the unit used to configure heap sizes.
For example -Xmx500m will result in a max heap size of 500 MiB = 500 \* 1024 \* 1024 bytes.

Note that parsing the object graph and computing the total size is a slow operation. This is not something you want to
do in production.
My advice is to measure it locally during development using realistic data, or use a feature flag to only enable it
in test environments.

Measuring the memory footprint for a couple cache size should be enough.
The relation between the no. of cache entries and memory footprint should be nearly linear.
Based on the measurements you can compute how many bytes on average are required for each element in the cache with some
simple math or a linear regression.

What to measurure: bytes / cache element, cache utilization curve.

Cache utilization is an important metric when using caches. Often the utilization can be increased by permitting larger
caches.
However increasing the cache size will require more memory.
Once you know how many bytes are required on average per element, you can estimate how much memory will be needed.
For larger caches you may run into limits: the machine of vm might not offer enough memory, forcing you to upgrade to a
more expensive machine.
By optimizing the representation of objects stored in the cache, you can lower the memory footprint by a good amount.
This might enable you to get good cache utilization without throwing resources at the problem.

<a id="org1a1e770"></a>

### Optimizing memory usage

Often it's possible to reduce the memory footprint of an object by a factor without much work.
It will require writing customized classed especially for use in caches.
There is some impact on readability and the result might be somewhat less idiomatic, but in some cases the savings could
be worth it.
If it means you can lower your memory requirements by 3x from 6g to 2g you can run your application on cheaper machines,
Alternatively, you can store 3x more entries in the cache to improve cache utilization and reduce load on your
databases.

Now for many application you won't need this, or you can just spend a little extra on some extra memory.
But there are cases where a little optimization will be worth it.

My recommendation is to introduce an optimized class just for use in the cache, which conforms to the same interface as
the domain object.
You can then apply these techniques to reduce the memory footprint.

#### Only store essential fields

Often only a subset of the field are required to answer the queries.
By only including the fields that are not needed is an easy method to lower the memory footprint.
In the example, the original Timeline class had a createdAt field which is not used, so we get rid of it in the
TimelinePrimitive and TimelinePrimitiveArrays classes.

#### Use the smallest datatype that works.

In the example project, the idiomatic implementation uses java.time.Instant, which have a long (64-bit) and int (32-bit)
field
to store seconds and nanoseconds.
<https://github.com/openjdk/jdk21/blob/master/src/java.base/share/classes/java/time/Instant.java#L257-L262>
Lets pretend that here milliseconds are precise enough, so replacing Instants with longs can save an int plus the
overhead
from adding an object. The TimelinePrimitive class replaces Instants with longs.

#### Flatten object trees:

Every object uses some bytes of overhead, how many is implementation specific.
Usually it is 12 bytes on modern 64-bit JVM implementations.
The TimelinePrimitiveArrays class stores the start, end and value classes into arrays, so no intermediate Window object
are needed.

#### Trim collections

By default the backing array of an ArrayList doubled in size when you add an element and the backing array is full,
so the array may take up more space than needed.
You can use ArrayList.trimToSize() to trim unused parts the backing array.

Alternatively, you can ensure the array has the right size when constructing it.
Most stream methods to build an array, like LongStream.toArray(), already do this.

<a id="orge54cdc5"></a>

### Measuring the impact of optimizations:

When doing any kind of optimizing you should always measure the results,
so you know if the effort is worth it and when you have reached your goals.

By comparing the memory footprint of the different implementations, we can check how well the optimizations work.

In the example project, I use three different implementations of a class:
one 'idiomatic' java, one a little optimized, and one using primitive arrays.
These classes offer the same functionality, but with different memory footprints:

Some mock data is generated, and converted to the different representation.
I then use JOL's GraphLayout.totalSize() to measure the memory footprint for each representation.

Running the main function of the demo prints:

    Idiomatic:
    - elements: 100000
    - total memory footprint: 159.15380859375 MiB
    - avg memory / element: 1668.84864 bytes / element
    Primitive fields:
    - elements: 100000
    - total memory footprint: 82.69098663330078 MiB
    - avg memory / element: 867.07784 bytes / element
    Primitive arrays:
    - elements: 100000
    - total memory footprint: 54.46696472167969 MiB
    - avg memory / element: 571.12752 bytes / element

We see that implementation based on primitive arrays uses 3x less memory than the idiomatic implementation.
The code is a little less idiomatic, but still very readable in my opinion, and you can store 3x more entries, so thats
a good improvement.

<a id="org5a887f9"></a>

### Background reading:

An extensive guide on Java memory footprints, also uses JOL:
<https://shipilev.net/jvm/objects-inside-out/>

A simple introduction to memory layout of objects:
<https://www.baeldung.com/java-memory-layout>

<a id="org187eaaa"></a>

### Consulting

If you are looking for someone who can implement these types of optimizations in your Java, Kotlin or Clojure projects,
contact optimize@chrisblom.net

