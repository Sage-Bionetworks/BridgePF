module.exports = function(grunt) {
    'use strict';

    grunt.initConfig({
        hub: {
            options: {
                concurrent: 4 // as many as there are projects.
            },
            all: {
                src: ['shared/Gruntfile.js', 'app/Gruntfile.js', 'neurod/Gruntfile.js', 'consent/Gruntfile.js'],
                tasks: ['test','build','default','release','watch','clean']
            },
        },
    });

    grunt.loadNpmTasks('grunt-hub');
    grunt.registerTask('test', ['hub:all:test']);
    grunt.registerTask('build', ['hub:all:build']);
    grunt.registerTask('default', ['hub:all:default']);
    grunt.registerTask('release', ['hub:all:release']);
    grunt.registerTask('watch', ['hub:all:watch']);
    grunt.registerTask('clean', ['hub:all:clean']);
};
