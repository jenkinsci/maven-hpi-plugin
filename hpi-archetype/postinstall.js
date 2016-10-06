#!/usr/bin/env node

/**
 * Postinstall script that adds/install the latest version of "special"
 * Jenkins related packages to the project package.json and then deletes itself after,
 * so as to never be run again.
 */

const npm = require('npm');
const fs = require('fs');

npm.on("log", function (data) {
    process.stdout.write(data);
});
npm.on('error', function(err) {
    process.stderr.write(err);
});

function npmRun(script, onComplete) {
    npm.load(function (loadError) {
        if (loadError) {
            return process.exit(loadError);
        }
        npm.commands.run([script], function (runError) {
            if (runError) {
                return process.exit(runError);
            }
            onComplete();
        });
    });
}

// Do the installs
npmRun('do_prod_installs', function() {
    npmRun('do_dev_installs', function() {
         const packageJson = JSON.parse(fs.readFileSync('package.json', 'utf8'));

         // Remove the postinstall script so we don't execute it again.
         delete packageJson.scripts.postinstall;
         delete packageJson.scripts.do_prod_installs;
         delete packageJson.scripts.do_dev_installs;

         fs.writeFileSync('package.json', JSON.stringify(packageJson, undefined, 2), 'utf8');

         // And zap this script.
         fs.unlinkSync(__filename);
    });
});
