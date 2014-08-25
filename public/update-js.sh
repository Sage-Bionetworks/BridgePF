# Removed running bower from here, but there are some notes that running Bower as a 
# postinstall process itself can introduce errors, so we may need to revert. It did 
# not fix deployment to Heroku.
bower cache clean
cd app/
npm install
bower install
cd ../shared
npm install
bower install
cd ../neurod
npm install
bower install
cd ../consent
npm install
bower install
cd ..
npm install
bower install
