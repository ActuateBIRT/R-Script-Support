# R-Script-Support
Extension to support R language features in BIRT and iHub

## Overview
This project builds plugins to support R language features in OpenText Analytics ® Information Hub (iHub) and BIRT-based products such as BIRT Designer Professional (BDPro).
The plugins allow BIRT to connect to and query an R installation by using the [Rserve](https://www.rforge.net/Rserve) client/server communication protocol.

## Build
This project is built with [Apache Maven](http://maven.apache.org). To do a complete rebuild, go to the root of the project, and run

    mvn clean package 

This produces the following 2 artifacts in the _releng/com.actuate.birt.script.r.support.update/target_ directory:
* __com.actuate.birt.script.r.support.update-24.2.0-SNAPSHOT.zip__

  This ZIP file is the content of Eclipse update site containing all the features and plugins for R support. It can be used by BIRT Designer Professional (an Eclipse-based desktop application) to install the R Support feature.
  
* __repository/plugins/com.actuate.birt.script.ext.rserve_24.2.0.vnnnnnnnnnnnn.jar__

  This jar file is the plugin that can be added to OpenText Analytics Information Hub to enable R Support feature. 
  
