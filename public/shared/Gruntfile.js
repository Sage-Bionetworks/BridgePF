'use strict';

module.exports = function(grunt) {

    require('load-grunt-tasks')(grunt);
    
    var jsFiles = [
        "bower_components/angular/angular.js",
        "bower_components/angular-route/angular-route.js",
        "bower_components/bootstrap-bower/ui-bootstrap-tpls.min.js",
        "scripts/humane-modified.js",
        "scripts/shared.js",
        "scripts/form-service.js",
        "scripts/humane.js",
        "scripts/auth-service.js",
        "scripts/signin-service.js",
        "scripts/reset-password-service.js",
        "scripts/auth-interceptor.js",
        "scripts/loading-interceptor.js",
        "scripts/ga.js",
        "scripts/bg-focus.js"
    ];
    var cssFiles = [
        'bower_components/bootstrap/dist/css/bootstrap.css',
        'bower_components/bootstrap/dist/css/bootstrap-theme.css',
        'styles/*.scss'
    ];
    
    grunt.initConfig({
        token: "bridge-shared",
        output: "build",
        
        clean: {
            build: ['<%= output %>'],
            release: ['node_modules', 'bower_components']
        },
        jshint: {
            options: { node: true, loopfunc: true, globals: { "angular": false } },
            js: ['scripts/**/*.js']
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
            },
            sass: {
                src: cssFiles,
                dest: '<%= output %>/<%= token %>.scss',
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
        copy: {
            main: {
                files: [
                    {
                        expand: true, cwd: 'bower_components/bootstrap/dist/css/', src: ['*.map'], 
                        dest: '<%= output %>'
                    }
                ]
            }
        },
        watch: {
            all: {
                files: ['Gruntfile.js', 'scripts/*.js', 'styles/**/*.scss'],
                tasks: 'build',
                spawn: false
            }
        }
    });

    grunt.registerTask('test', ['build']);
    grunt.registerTask('build', ['jshint', 'clean:build', 'concat', 'sass', 'uglify', 'copy']);
    grunt.registerTask('default', ['jshint', 'clean:build', 'concat', 'sass', 'uglify', 'copy']);
    grunt.registerTask('release', ['test', 'clean:release']);
};
