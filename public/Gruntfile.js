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
                src: [
                    'app/bower_components/angular-bootstrap/ui-bootstrap-tpls.js',
                    'app/bower_components/humane-js/humane.js',
                    'app/scripts/app.js',
                    'app/scripts/auth-service.js', 
                    'app/scripts/dialogs/*.js',
                    'app/scripts/controllers/*.js'
                ],
                dest: '<%= output %>/bridge.js',
                nonull: true
    	    },
    	    sass: {
    	    	src: [
    	    	    'app/bower_components/bootstrap/dist/css/bootstrap.css',
    	    	    'app/bower_components/bootstrap/dist/css/bootstrap-theme.css',
    	    	    'app/styles/humane-modified.css', // to work with bootstrap
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
    			tasks: 'default',
    			spawn: false
    		}
    	}
	});
	
	grunt.registerTask('test', ['jshint','concat','jasmine']);
	grunt.registerTask('default', ['jshint','concat','sass','uglify']);
	
};
