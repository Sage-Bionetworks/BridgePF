'use strict';

module.exports = function(grunt) {

    require('load-grunt-tasks')(grunt);
    
    var jsFiles = [
        "scripts/humane-modified.js",
        "scripts/shared.js",
        "scripts/form-service.js",
        "scripts/humane.js",
        "scripts/auth-service.js",
        "scripts/signin-service.js",
        "scripts/reset-password-service.js",
        "scripts/auth-interceptor.js",
        "scripts/loading-interceptor.js"
    ];

    grunt.initConfig({
        token: "bridge-auth",
        output: "build",
        
        clean: {
            build: ['<%= output %>'],
            release: ['node_modules', 'bower_components']
        },
        jshint: {
            options: { node: true, loopfunc: true, globals: { "angular": false } },
            js: jsFiles
        },
        sass: {
            all: {
                files: { '<%= output %>/<%= token %>.css' : '<%= output %>/<%= token %>.scss' }
            }
        },
        concat: {
            js: {
                src: jsFiles,
                dest: '<%= output %>/<%= token %>.js',
                nonull: true
            }
        },
        uglify: {
            js: {
                src: '<%= output %>/<%= token %>.js',
                dest: '<%= output %>/<%= token %>.min.js'
            },
            options: {
                sourceMap: true
            }
        },
        watch: {
            all: {
                files: ['Gruntfile.js', 'scripts/*.js'],
                tasks: 'build',
                spawn: false
            }
        }
    });

    grunt.registerTask('test', ['build']);
    grunt.registerTask('build', ['jshint', 'clean:build', 'concat', 'uglify']);
    grunt.registerTask('default', ['jshint', 'clean:build', 'concat', 'uglify']);
    grunt.registerTask('release', ['test', 'clean:release']);
};
