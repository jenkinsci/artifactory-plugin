/**
 * @author Lior Hasson
 */

function virtualRepos(button, paramList, artifactoryUrl, deployUsername, deployPassword, overridingDeployerCredentials, bind) {
    button = button._button;
    var spinner = $(button).up("DIV").next();
    spinner.style.display = "block";
    var target = spinner.next();
    target.innerHTML = "";

    bind.refreshVirtualRepo(artifactoryUrl, deployUsername, deployPassword, overridingDeployerCredentials, function (t) {

        if (t.responseObject().length === 0) {
            spinner.style.display = "none";
            target.innerHTML = "Unable to connect Artifactory!";
            target.addClassName('error');
        }
        else {
            paramList.split(',').each(function (controlTagId) {
                var select = $(controlTagId);
                while (select.firstChild) {
                    select.removeChild(select.firstChild);
                }
                ;

                t.responseObject().forEach(function (item) {

                    var option = document.createElement("option");
                    option.text = item.displayName;
                    option.value = item.value;
                    //var sel = $(controlTagId).options[$(controlTagId).selectedIndex];
                    select.appendChild(option);
                });
            });
            spinner.style.display = "none";
            target.innerHTML = "Repositories refreshed successfully";
            target.removeClassName('error')
        }
    });
};

