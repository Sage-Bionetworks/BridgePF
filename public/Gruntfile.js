/**
 * Example Grunt Hub
 *
 * Edit the hub.all.src to point to your Gruntfile locations.
 * Then run `grunt`.
 */
module.exports = function(grunt) {
    'use strict';

    grunt.initConfig({
        hub: {
            all: {
                src: ['shared/Gruntfile.js', 'app/Gruntfile.js', 'neurod/Gruntfile.js'],
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
