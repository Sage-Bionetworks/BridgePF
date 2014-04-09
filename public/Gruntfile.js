'use strict';

module.exports = function(grunt) {

	// Load grunt tasks automatically
	require('load-grunt-tasks')(grunt);

	grunt.initConfig({
		output: "app/build",
		pkg: grunt.file.readJSON('package.json'),
		
		clean: {
			all: ['<%= output %>']
		},
    	jshint: {
    		options: { node: true, globals: { "angular": false } },
    	    js: ['app/scripts/**/*.js']
    	},
    	sass: {
    		all: {
    			files: { '<%= output %>/bridge.css' : '<%= output %>/bridge.scss' }
    		}
    	},
		concat: {
    	    js: {
                src: ['app/scripts/app.js', 'app/scripts/controllers/*.js', 'app/scripts/analytics/*.js'],
                dest: '<%= output %>/bridge.js'
    	    },
    	    sass: {
    	    	src: ['app/styles/*.scss', 'app/styles/*.css'],
    	    	dest: '<%= output %>/bridge.scss'
    	    }
    	},
    	uglify: {
    		js: {
    			src: '<%= output %>/bridge.js',
    			dest: '<%= output %>/bridge.min.js'
    		}
    	},
        jasmine: {
            src: [ 
                'app/bower_components/angular/angular.js',
                'app/bower_components/angular-mocks/angular-mocks.js',
                '<%= output %>/bridge.min.js'
            ],
            options: {
                version: '2.0.0',
                specs: ['test/**/*_spec.js']
            }
        },
    	// Run jasmine tests through ghostdriver on the command line
    	
    	// run 'grunt watch' to have files processed any time they are changed while you work.
    	watch: {
    		all: {
    			files: ['app/srcipts/*.js', 'app/styles/*.scss', 'app/styles/*.css'],
    			tasks: 'default',
    			spawn: false
    		}
    	}
	});
	
	grunt.registerTask('test', ['jshint','concat','jasmine']);
	grunt.registerTask('default', ['jshint','concat','sass','uglify']);
	
};
