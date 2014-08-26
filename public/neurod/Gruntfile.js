'use strict';

module.exports = function(grunt) {

    require('load-grunt-tasks')(grunt);
    
    var jsFiles = [
        "scripts/neurod.js",
        "scripts/controllers/*.js",
        "scripts/directives/*.js"
    ];

    grunt.initConfig({
        token: "neurod",
        output: "build",
        
        clean: {
            build: ['<%= output %>']
        },
        jshint: {
            options: { node: true, loopfunc: true, globals: { "angular": false } },
            js: jsFiles
        },
        sass: {
            all: {
                files: { '<%= output %>/<%= token %>.min.css' : '<%= output %>/<%= token %>.scss' }
            }
        },
        concat: {
            js: {
                src: jsFiles,
                dest: '<%= output %>/<%= token %>.js',
                nonull: true
            },
            sass: {
                src: [
                    "styles/neurod.scss",
                    "styles/bootstrap-tweaks.scss",
                    "styles/carousel.scss",
                    "styles/section-main.scss",
                    "styles/section-about.scss",
                    "styles/section-join.scss",
                    "styles/section-joined.scss",
                    "styles/navigation.scss"
                ],
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
        hashres: {
            options: {
                encoding: 'utf8',
                fileNameFormat: '${name}.${hash}.${ext}',
                renameFile: true
            },
            execute: {
                src: ['<%= output %>/*.min.js', '<%= output %>/*.min.css'],
                dest: ['../../app/views/<%= token %>.scala.html']
            }
        },
        watch: {
            all: {
                files: ['Gruntfile.js', 'scripts/**/*.js', 'styles/**/*.scss', 'styles/**/*.css'],
                tasks: 'build',
                spawn: false
            }
        }
    });

    grunt.registerTask('test', ['build']);
    grunt.registerTask('build', ['jshint', 'clean:build', 'concat', 'sass', 'uglify', 'hashres']);
    grunt.registerTask('default', ['jshint', 'clean:build', 'concat', 'sass', 'uglify', 'hashres']);
    grunt.registerTask('release', ['build']);
};
