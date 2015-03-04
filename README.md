Bridge (Sage Bionetworks)
=========================================

[![Build Status](https://travis-ci.org/Sage-Bionetworks/BridgePF.svg?branch=develop)](https://travis-ci.org/Sage-Bionetworks/BridgePF)

Documentation
-------------
REST API - [see here](https://sagebionetworks.jira.com/wiki/display/BRIDGE/Bridge+REST+API)
Java SDK - [see here](https://github.com/Sage-Bionetworks/BridgeJavaSDK)
iOS SDK  - [see here](https://github.com/Sage-Bionetworks/Bridge-iOS-SDK)

Installation
------------

Install Play Framework 2.2.x, node + npm, and bower (npm install -g bower). 

To get up and running, first start play and generate the Eclipse meta 
files:

    play (starts play CLI)
    eclipse (within the CLI)

It should be possible to import the project as an existing project in Eclipse. 
Play will handle Java-based dependencies. 

Tests
-----

For Play tests (including integration tests using PhantomJS and Selenium), run

    play test
