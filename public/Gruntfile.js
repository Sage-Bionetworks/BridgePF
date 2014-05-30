'use strict';

module.exports = function(grunt) {

    // Load grunt tasks automatically
    require('load-grunt-tasks')(grunt);

    grunt.initConfig({
        output: "app/build",
        pkg: grunt.file.readJSON('package.json'),
        
        clean: {
            build: ['<%= output %>'],
            release: ['node_modules', 'app/bower_components']
        },
        jshint: {
            options: { node: true, loopfunc: true, globals: { "angular": false } },
            js: ['app/scripts/**/*.js']
        },
        sass: {
            all: {
                files: { '<%= output %>/bridge.css' : '<%= output %>/bridge.scss' }
            }
        },
        concat: {
            js: {
                src: [
                    'app/bower_components/angular-bootstrap/ui-bootstrap-tpls.js',
                    'app/bower_components/dygraphs/dygraph.dev.js',
                    'app/scripts/humane-modified.js',
                    'app/scripts/app.js',
                    'app/scripts/services/*.js',
                    'app/scripts/directives/*.js',
                    'app/scripts/controllers/*.js',
                    'app/scripts/controllers/directives/*.js'
                ],
                dest: '<%= output %>/bridge.js',
                nonull: true
            },
            sass: {
                src: [
                    'app/bower_components/bootstrap/dist/css/bootstrap.css',
                    'app/bower_components/bootstrap/dist/css/bootstrap-theme.css',
                    'app/styles/humane-modified.css', // to work with bootstrap
                    'app/styles/*.css',
                    'app/styles/*.scss'
                ],
                dest: '<%= output %>/bridge.scss',
                nonull: true
            }
        },
        uglify: {
            js: {
                src: '<%= output %>/bridge.js',
                dest: '<%= output %>/bridge.min.js'
            },
            options: {
                sourceMap: true
            }
        },
        // Run jasmine tests through ghostdriver on the command line
        jasmine: {
            src: [ 
                'app/bower_components/angular/angular.js',
                'app/bower_components/angular-mocks/angular-mocks.js',
                'app/bower_components/angular-route/angular-route.js',
                '<%= output %>/bridge.min.js'
            ],
            options: {
                version: '2.0.0',
                specs: ['test/**/*_spec.js']
            }
        },
        // run 'grunt watch' to have files processed any time they are changed while you work.
        watch: {
            all: {
                files: ['Gruntfile.js', 'app/scripts/**/*.js', 'app/styles/**/*.scss', 'app/styles/**/*.css'],
                tasks: 'build',
                spawn: false
            }
        }
    });

    grunt.registerTask('test', ['build', 'jasmine']);
    grunt.registerTask('build', ['jshint', 'clean:build', 'concat', 'sass', 'uglify']);
    grunt.registerTask('default', ['jshint', 'clean:build', 'concat', 'sass', 'uglify']);
    grunt.registerTask('release', ['test', 'clean:release']);
};
