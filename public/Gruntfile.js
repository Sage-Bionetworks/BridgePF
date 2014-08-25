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
            }
        },
        clean: ['node_modules']
    });

    grunt.loadNpmTasks('grunt-hub');
    grunt.loadNpmTasks('grunt-contrib-clean');
    grunt.registerTask('test', ['hub:all:test']);
    grunt.registerTask('build', ['hub:all:build']);
    grunt.registerTask('default', ['hub:all:default']);
    grunt.registerTask('release', ['hub:all:release', 'clean']);
    grunt.registerTask('watch', ['hub:all:watch']);
    grunt.registerTask('clean-all', ['hub:all:clean']);
};
