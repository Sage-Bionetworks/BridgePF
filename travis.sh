#!/bin/bash
set -ev
if [ "${TARGET}" -eq "JavaScript" ]
    then
        export JS_RELEASED=false
        npm install bower
        npm install grunt-cli
        gem install sass
        cd public
        npm install
        bower install
        grunt test
        grunt release
        cd ..
        export JS_RELEASED=true
    else
        sbt test
fi
