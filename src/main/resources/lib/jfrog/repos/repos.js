/**
 * @author Lior Hasson
 */

function repos(button, paramList, artifactoryUrl, deployUsername, deployPassword, overridingDeployerCredentials, bind) {
    button = button._button;
    var spinner = $(button).up("DIV").next();
    spinner.style.display = "block";
    var target = spinner.next();
    target.innerHTML = "";
    var warning = target.next();
    warning.innerHTML = "";

    bind.refreshRepo($(artifactoryUrl).value, deployUsername, deployPassword, overridingDeployerCredentials, function (t) {
        var response = t.responseObject();
        if (!response.success) {
            spinner.style.display = "none";
            target.innerHTML = response.responseMessage;//"Connection with Artifactory server failed!";
            target.addClassName('error');
            target.style.color = "red";
        }
        else {
            var oldValueExistsInNewList = true;
            paramList.split(',').each(function (controlTagId) {
                var select = $(controlTagId);
                var old = $(controlTagId).clone(true);
                while (select.firstChild) {
                    select.removeChild(select.firstChild);
                }

                response.repos.forEach(function (item) {

                    var option = document.createElement("option");
                    option.text = item;
                    option.value = item;
                    //var sel = $(controlTagId).options[$(controlTagId).selectedIndex];
                    select.appendChild(option);
                });

                if (oldValueExistsInNewList)
                    oldValueExistsInNewList = compareSelectTags(select, old);
            });
            spinner.style.display = "none";
            target.innerHTML = "Repositories refreshed successfully";
            target.removeClassName('error');
            target.style.color = "green";

            if (!oldValueExistsInNewList) {
                warning.innerHTML = "Warning! One of your previously configured repositories does not exist.";
                warning.style.color = "orange"
            }
        }
    });
};

function compareSelectTags(newRepos, oldRepos) {
    if (oldRepos.options.length == 0)
        return true;

    var ans = true;
    [].forEach.call(oldRepos, function (elmOld) {
        var flag = false;
        [].forEach.call(newRepos, function (elmNew) {
            if (elmNew.value === elmOld.value && !flag)
                flag = true;
        });
        if (!flag) {
            ans = flag;
            return ans;
        }

    });
    return ans;
}

