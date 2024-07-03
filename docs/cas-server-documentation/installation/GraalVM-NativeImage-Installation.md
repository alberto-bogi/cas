---
layout: default
title: CAS - Graal VM Native Image Installation
category: Installation
---
{% include variables.html %}

# Graal VM Native Image Installation

[Graal VM Native Images](https://www.graalvm.org/native-image/) are standalone executables that can be generated by 
processing compiled Java applications ahead-of-time. Native Images generally have a smaller memory footprint and start faster than their JVM counterparts.

A CAS server installation and deployment process can be tuned to build and run as a Graal VM native image under a dedicated `native` profile. 
Compared to the Java Virtual Machine, CAS server native images can run with a smaller memory footprint and with much faster startup times. 
A CAS Graal VM Native Image is a complete, platform-specific executable. You do not need to ship a Java Virtual Machine in order to 
run a CAS native image. It requires and uses ahead-of-time (AOT) processing in order to 
create an executable. This ahead-of-time processing involves statically analyzing CAS application code from its main entry point.

<div class="alert alert-warning">:warning: <strong>Usage Warning!</strong><p>
This capability is a work in progress, requires a lot of trial and error and is highly experimental at this point. We encourage you to start 
to experiment and test your CAS deployment with this feature and contribute fixes.</p></div>

There are some key differences between native and JVM-based CAS deployments. The main differences are:

- Static analysis of the CAS server is performed at build-time from the main entry point.
- Code that cannot be reached when the native image is created will be removed and won’t be part of the executable.
- Graal VM is not directly aware of dynamic elements in CAS code and must be told about reflection, resources, serialization, and dynamic proxies.
- The CAS server application classpath is fixed at build time and cannot change.
- There is no lazy class loading and everything shipped in the final executable will be loaded in memory on startup.

During the AOT processing phase, the CAS web application is started up to the point that *bean definitions* 
are available. *Bean instances* are NOT created during the AOT processing phase. 

The AOT process, integrated directly into the CAS build, will typically generate:

- Java source code under `build/generated/aotSources`
- Bytecode (for dynamic proxies etc) under `build/generated/aotClasses`
- Graal VM JSON hint files
  - Resource hints (`resource-config.json`)
  - Reflection hints (`reflect-config.json`)
  - Serialization hints (`serialization-config.json`)
  - Java Proxy Hints (`proxy-config.json`)
  - JNI Hints (`jni-config.json`)
  
Note that Hint files that are put under `src/main/resources/META-INF/native-image` are automatically picked up by Graal VM native image tool.
Generated hint files can typicallt be found in `build/generated/aotResources`.

## System Requirements

A Graal VM distribution compatible with [CAS requirements](../planning/Installation-Requirements.html) must be present on the build machine
and activated as the operating Java runtime. Presently and at a minimum, you will need to have Graal VM installed with 
the [native image tool](https://www.graalvm.org/latest/reference-manual/native-image/) in place, in case your Graal VM
distribution does not offer and package this by default.
     
Needless to say, the ability to work with Graal VM native image is and will only be available in CAS deployments
that run with an [embedded server container](../installation/Configuring-Servlet-Container-Embedded.html).
When building a CAS Graal VM native image, an embedded server container will be automatically provided.
 
The build machine that ultimately produces the CAS Graal VM native image is preferred be running Linux 
with at least 16GB of memory and 4 CPU cores.

<div class="alert alert-info">:information_source: <strong>LLVM Toolchain</strong><p>
You may run into build errors about <i>ld64 limitations</i>, particularly if you are building native images on ARM machines.
The build process may ask you to use <code>ld64.lld</code> via <code>gu install llvm-toolchain</code>.
</p></div>

## Installation

The ability to build Graal VM native images is built directly into the CAS installation process. The installation script
can be downloaded and prepped using the [CAS Initializr](../installation/WAR-Overlay-Initializr.html). The produced project will
contain a `README` file with instructions on how to build and run CAS native images.

<div class="alert alert-info">:information_source: <strong>Build Time</strong><p>
Building CAS Graal VM native images can be quite resource intensive and time consuming. Depending on the number of modules
included in the build, CAS configuration options and the horsepower of the build machine and available memory, the build time can vary greatly
and typically is in the neighborhood of <code>10~20</code> minutes and perhaps longer.</p></div>

Since in AOT and native mode, configuration is being processed and the context is being optimized at build time,
any properties that would influence bean creation (such as the ones used within the bootstrap context) should be set
to the same values at build time and runtime to avoid unexpected behaviour. While building a CAS deployment that contains 
the Spring Cloud Config Client, you must make sure that the configuration data source that it connects to is available at build time. 
For example, if you retrieve configuration data from Spring Cloud Config Server, make sure you have its 
instance running and available at the port indicated in the Config Client setup.

## Known Limitations

CAS Graal VM native images are an evolving technology. Not all libraries used by CAS and not all modules offered by CAS
provide support for native images. Additionally, the following scenarios are unsupported or do require a lot of finesse
and maneuvering to function:
   
- Apache Log4j does not support native images; [Logback](../logging/Logging-Logback.html) is used instead by default.      
- All capabilities and features that load, parse and execute [Apache Groovy scripts](../integration/Apache-Groovy-Scripting.html), or load dynamic code constructs.
- Libraries and dependencies written in Groovy or other dynamic languages will be extremely challenging to support.
- All capabilities and features that load CAS configuration properties from external sources that are backed by Spring Cloud.
- Refresh scope and dynamically refreshing the application context is not supported with CAS native images.

If you find a library which does not work with Graal VM, please discuss that issue
on the [reachability metadata project](https://github.com/oracle/graalvm-reachability-metadata).

Note while the startup time is orders of magnitude faster than on the traditional JVM, 
the actual latency and throughput may be worse on the native image - there is no JIT compiler that optimizes 
code execution paths in runtime. Ideally, you should run performance tests to find out how CAS behaves 
as a native image vs a traditional JVM application.
 
### Apache Groovy

Given the dynamic nature of the Apache Groovy programming language and it meta programming model, you will find
that almost all capabilities and features in CAS that load, parse and execute Groovy scripts of any form or load dynamic code constructs
in Groovy snippets will either not work at all, or will have to be rewritten so they may be *statically* compiled by the Groovy parser.
While in native image mode, CAS will forcefully and automatically switch the Groovy compiler configuration to use Groovy's 
static compilation feature which in some case seems to assist with native image compilation.

<div class="alert alert-info">:information_source: <strong>Remember</strong><p>Again, this only 
works in some cases and will most certainly not be a bulletproof solution. Fixes and enhancements in this area will
certainly changes to Apache Groovy and/or Graal VM's native image compiler and AOT processing itself none of which
carry any weight or scope here.</p></div>

To learn more about Apache Groovy in CAS, please [see this guide](../integration/Apache-Groovy-Scripting.html).

## Native Image Hints

If you need to provide your own hints for reflection, resources, serialization, proxy usage etc. 
you can define your own class that implements the `CasRuntimeHintsRegistrar` API, with the following outline:

```java
package org.example;

public class MyRuntimeHints implements CasRuntimeHintsRegistrar {
    @Override
    public void registerHints(RuntimeHints hints, 
                              ClassLoader classLoader) {
        // Register your own hints...
    }
}
```

You can then use `@ImportRuntimeHints` on any `@AutoConfiguration` class to activate such hints. Alternatively,
you may create the file `src/main/resources/META-INF/spring/aot.factories` with the following contents:

```properties
org.springframework.aot.hint.RuntimeHintsRegistrar=org.example.MyRuntimeHints
```

CAS itself will provide a large body of native image hints for many of modules found in the codebase. This process and native image
support coverage is not exhaustive and you may be asked to register your own hints for components, APIs and processes
that are absent in CAS-provided hints. If you do run into such scenarios, consider contributing those hints
back to the CAS project directly if the hint belongs or affects a CAS-owned component, or discuss the issue with the
[reachability metadata project](https://github.com/oracle/graalvm-reachability-metadata).