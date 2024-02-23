# Table of Contents

1. [Optimizing the memory footprint of caches with JOL](#orgd70b69d)
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

# Optimizing the memory footprint of caches with JOL

Lots of applications use some type of in-memory storage.
A common use case is caching results gathered from an external source, like a database or another service.
Another use case is loading static resources from disk when the application starts.

To prevent your application from running out of memory it is a good idea to know what the memory footprint of this
storage is.

Usually the cache has some settings to limit maximum number of entries. How can you determine how much memory is used at most?
Alternatively, what can you set this limit to maximize the usage of the available memory? 

To answer these questions you need to determine the average memory footprint of an entry in the cache.
This is simple in theory, but somewhat tricky in practice: you could just walk the object graph and recursively sum
the footprint of all the fields, and divide it by the number of entries. 
However the exact size of objects is hard to determine, each object requires some overhead which may vary per JVM
implementation and can even depend on the used JVM options.

Rather then depending on rough estimates, it is best to measure how much memory is used.
There is a tool called jol which traverses an object graph and sums the sizes, which gives
you an accurate measurement of the memory footprint.

<a id="orgb95b1d8"></a>

### Measuring the memory footprint of a cache:

In the example project I setup a mock cache, which is populated with generated data.
Just pretend the entries come from a database or some external service.

To measure the total memory usage of an object and all it references we use jol's GraphLayout.totalSize():

    System.out.println(GraphLayout.parseInstance(cache).totalSize() / 1024d / 1024d + " MiB");

This will report the total size in MiB (mebibyte), which is also the unit used to configure heap sizes,
for example -Xmx500m will result in a max heap size of 500 MiB = 500 \* 1024 \* 1024 bytes.

Note that parsing the object graph and computing the total size is a slow operation. This is not something you want to
run often in production.
My advice is to measure it locally during development using realistic data, or use a feature flag to only enable it
in test environments. However it is important that the cache is filled with data that has similar sizes as in production.

Once you know the total size, the average size per entry is easily calculated and you can use this to estimate how much 
memory will be needed to support different cache sizes.  
For larger caches you may run into limits: the machine of vm might not offer enough memory, forcing you to upgrade to a
more expensive machine.
By optimizing the representation of objects stored in the cache, you can lower the memory footprint by a good amount.
This might enable you to get good cache utilization without throwing resources at the problem.

<a id="org1a1e770"></a>

### Optimizing memory usage

Often it is possible to reduce the memory footprint of an object by a factor without too much work.
It will require writing customized classed especially for use in the caches.
There is some impact on readability and the result might be somewhat less idiomatic, but in some cases the savings could
be worth it.
If it means you can lower your memory requirements by 3x from 6000 MiBto 2000 MiB you can run your application on cheaper machines. 
Memory can be expensive, especially in cloud environments. 
Besides lowering costs, optimizing the memory footprint of caches can also enable you to store more entries while using 
the same amout of memory. Larger caches may improve cache utilization and lower the overall load on the system by avoiding 
calls to external services or databases.

Optimizing the memory footprint of caches is a bit of a niche technique, for most applications it is not worth the effort, 
but for applications that process lots of requests the savings could be significant.

My recommendation is to introduce an optimized class just for use in the cache, which conforms to the same interface as
the domain object. You can then apply these techniques to reduce the memory footprint.

In the demo project I added several implementations of the Timeline interface, using some of these optimizations: 

#### Use the smallest datatype that works.

In the example project, the simple implementation uses java.time.Instant to represent the start and end of an interval.
The memory footprint of an Instant is composed out of a long (8 bytes) and an int (4 bytes), plus the overhead of an object (12 bytes).
Assuming that a precision of milliseconds is sufficient, we can replace an Instant with just a long. 

#### Only store essential fields

Often only a subset of the field are required to be stored in the cache.
By only including the required fields we can reduce the memory footprint.

In the demo project, the TimelineSimple class had a createdAt field which is not used, so it is omitted in the
TimelinePrimitive and TimelinePrimitiveArrays classes.

#### Flatten object trees:

Every object uses some bytes of overhead, how many is implementation specific.
Usually it is 12 bytes on modern 64-bit JVM implementations.
The TimelinePrimitiveArrays class stores the start, end and value classes into arrays, so no intermediate Window object
are needed.

#### Trim collections

By default the backing array of an ArrayList doubles in size when you add an element and the backing array is full,
so the array may take up more space than needed.
You can use ArrayList.trimToSize() to trim unused parts the backing array.

Alternatively, you can ensure the array has the right size when constructing it.
Most stream methods to build an array, like LongStream.toArray(), already do this.

<a id="orge54cdc5"></a>

### Measuring the impact of optimizations:

When doing any kind of optimizing you should always measure the results,
so you know if the effort is worth it, or when you have reached your goals.

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
The code is a little less idiomatic, but still readable in my opinion, and you can store 3x more entries, so that is
a good improvement.

<a id="org5a887f9"></a>

### Background reading:

An extensive guide on Java memory footprints, also uses JOL:
<https://shipilev.net/jvm/objects-inside-out/>

A simple introduction to memory layout of objects:
<https://www.baeldung.com/java-memory-layout>

<a id="org187eaaa"></a>

### Consulting

If you are looking for someone who can reduce costs or speed up your system with these types of optimizations contact
optimize@chrisblom.net

