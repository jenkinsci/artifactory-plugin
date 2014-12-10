/**
 * @author Lior Hasson
 */

function repos(button, jsFunction, artifactoryUrl, deployUsername, deployPassword, overridingDeployerCredentials, bind) {
    button = button._button;
    var spinner = $(button).up("DIV").next();
    spinner.style.display = "block";
    var target = spinner.next();
    target.innerHTML = "";
    var warning = target.next();
    warning.innerHTML = "";

    if (jsFunction == "artifactoryIvyFreeStyleConfigurator") {
        artifactoryIvyFreeStyleConfigurator(spinner, $(artifactoryUrl).value, deployUsername, deployPassword, overridingDeployerCredentials, bind);
    } else
    if (jsFunction == "artifactoryGenericConfigurator") {
        artifactoryGenericConfigurator(spinner, $(artifactoryUrl).value, deployUsername, deployPassword, overridingDeployerCredentials, bind);
    } else
    if (jsFunction == "artifactoryMaven3NativeConfigurator") {
        artifactoryMaven3NativeConfigurator(spinner, $(artifactoryUrl).value, deployUsername, deployPassword, overridingDeployerCredentials, bind);
    } else
    if (jsFunction == "artifactoryMaven3Configurator") {
        artifactoryMaven3Configurator(spinner, $(artifactoryUrl).value, deployUsername, deployPassword, overridingDeployerCredentials, bind);
    } else
    if (jsFunction == "artifactoryGradleConfigurator") {
        artifactoryGradleConfigurator(spinner, $(artifactoryUrl).value, deployUsername, deployPassword, overridingDeployerCredentials, bind);
    } else
    if (jsFunction == "artifactoryRedeployPublisher") {
        artifactoryRedeployPublisher(spinner, $(artifactoryUrl).value, deployUsername, deployPassword, overridingDeployerCredentials, bind);
    } else
    if (jsFunction == "artifactoryIvyConfigurator") {
        artifactoryIvyConfigurator(spinner, $(artifactoryUrl).value, deployUsername, deployPassword, overridingDeployerCredentials, bind);
    }
}

function artifactoryIvyFreeStyleConfigurator(spinner, artifactoryUrl, deployUsername, deployPassword, overridingDeployerCredentials, bind) {
    bind.refreshFromArtifactory(spinner, artifactoryUrl, deployUsername, deployPassword, overridingDeployerCredentials, function (t) {
        var target = spinner.next();
        var warning = target.next();

        var response = t.responseObject();
        if (!response.success) {
            displayErrorResponse(spinner, target, response.responseMessage);
        }
        else {
            var select = document.getElementById("ivyFreeRepositoryKeys-" + artifactoryUrl);
            var oldValue = select.value;
            var oldSelect = select.cloneNode(true);
            removeElements(select);
            fillSelect(select, response.repositories);
            setSelectValue(select, oldValue);

            var oldValueExistsInNewList = compareSelectTags(select, oldSelect);
            if (!oldValueExistsInNewList) {
                displayWarningMessage(warning);
            }
            displaySuccessMessage(spinner, target);
        }
    });
}

function artifactoryGenericConfigurator(spinner, artifactoryUrl, deployUsername, deployPassword, overridingDeployerCredentials, bind) {
    bind.refreshFromArtifactory(spinner, artifactoryUrl, deployUsername, deployPassword, overridingDeployerCredentials, function (t) {
        var target = spinner.next();
        var warning = target.next();

        var response = t.responseObject();
        if (!response.success) {
            displayErrorResponse(spinner, target, response.responseMessage);
        }
        else {
            var select = document.getElementById("genericRepositoryKeys-" + artifactoryUrl);
            var oldValue = select.value;
            var oldSelect = select.cloneNode(true);
            removeElements(select);
            fillSelect(select, response.repositories);
            setSelectValue(select, oldValue);

            var oldValueExistsInNewList = compareSelectTags(select, oldSelect);
            if (!oldValueExistsInNewList) {
                displayWarningMessage(warning);
            }
            displaySuccessMessage(spinner, target);
        }
    });
}

function artifactoryMaven3NativeConfigurator(spinner, artifactoryUrl, deployUsername, deployPassword, overridingDeployerCredentials, bind) {
    bind.refreshFromArtifactory(spinner, artifactoryUrl, deployUsername, deployPassword, overridingDeployerCredentials, function (t) {
        var target = spinner.next();
        var warning = target.next();

        var response = t.responseObject();
        if (!response.success) {
            displayErrorResponse(spinner, target, response.responseMessage);
        }
        else {
            var selectRelease = document.getElementById("maven3NativeReleaseRepositoryKeys-" + artifactoryUrl);
            var selectSnapshot = document.getElementById("maven3NativeSnapshotRepositoryKeys-" + artifactoryUrl);

            var oldReleaseValue = selectRelease.value;
            var oldSnapshotValue = selectSnapshot.value;

            var oldSelectRelease = selectRelease.cloneNode(true);
            var oldSelectSnapshot = selectSnapshot.cloneNode(true);

            removeElements(selectRelease);
            removeElements(selectSnapshot);

            fillVirtualReposSelect(selectRelease, response.virtualRepositories);
            fillVirtualReposSelect(selectSnapshot, response.virtualRepositories);

            setSelectValue(selectRelease, oldReleaseValue);
            setSelectValue(selectSnapshot, oldSnapshotValue);

            selectRelease.onchange();
            selectSnapshot.onchange();

            var oldValueExistsInNewList = true;
            if (oldValueExistsInNewList) {
                oldValueExistsInNewList = compareSelectTags(selectRelease, oldSelectRelease);
            }
            if (oldValueExistsInNewList) {
                oldValueExistsInNewList = compareSelectTags(selectSnapshot, oldSelectSnapshot);
            }

            if (!oldValueExistsInNewList) {
                displayWarningMessage(warning);
            }
            displaySuccessMessage(spinner, target);
        }
    });
}

function artifactoryMaven3Configurator(spinner, artifactoryUrl, deployUsername, deployPassword, overridingDeployerCredentials, bind) {
    bind.refreshFromArtifactory(spinner, artifactoryUrl, deployUsername, deployPassword, overridingDeployerCredentials, function (t) {
        var target = spinner.next();
        var warning = target.next();

        var response = t.responseObject();
        if (!response.success) {
            displayErrorResponse(spinner, target, response.responseMessage);
        }
        else {
            var selectRelease = document.getElementById("maven3RepositoryKeys-" + artifactoryUrl);
            var selectSnapshot = document.getElementById("maven3SnapshotsRepositoryKeys-" + artifactoryUrl);

            var oldReleaseValue = selectRelease.value;
            var oldSnapshotValue = selectSnapshot.value;

            var oldSelectRelease = selectRelease.cloneNode(true);
            var oldSelectSnapshot = selectSnapshot.cloneNode(true);

            removeElements(selectRelease);
            removeElements(selectSnapshot);

            fillSelect(selectRelease, response.repositories);
            fillSelect(selectSnapshot, response.repositories);

            setSelectValue(selectRelease, oldReleaseValue);
            setSelectValue(selectSnapshot, oldSnapshotValue);

            var oldValueExistsInNewList = true;
            if (oldValueExistsInNewList) {
                oldValueExistsInNewList = compareSelectTags(selectRelease, oldSelectRelease);
            }
            if (oldValueExistsInNewList) {
                oldValueExistsInNewList = compareSelectTags(selectSnapshot, oldSelectSnapshot);
            }

            if (!oldValueExistsInNewList) {
                displayWarningMessage(warning);
            }
            displaySuccessMessage(spinner, target);
        }
    });
}

function artifactoryGradleConfigurator(spinner, artifactoryUrl, deployUsername, deployPassword, overridingDeployerCredentials, bind) {
    bind.refreshFromArtifactory(spinner, artifactoryUrl, deployUsername, deployPassword, overridingDeployerCredentials, function (t) {
        var target = spinner.next();
        var warning = target.next();

        var response = t.responseObject();
        if (!response.success) {
            displayErrorResponse(spinner, target, response.responseMessage);
        }
        else {
            var selectResolution = document.getElementById("gradleResolutionRepositoryKeys-" + artifactoryUrl);
            var selectPublish = document.getElementById("gradlePublishRepositoryKeys-" + artifactoryUrl);
            var selectPlugins = document.getElementById("gradleCustomStagingConfiguration-" + artifactoryUrl);

            var oldResolutionValue = selectResolution.value;
            var oldPublishValue = selectPublish.value;
            var oldPluginsValue = selectPlugins.value;

            var oldSelectResolution = selectResolution.cloneNode(true);
            var oldSelectPublish = selectPublish.cloneNode(true);
            var oldSelectPlugins = selectPlugins.cloneNode(true);

            removeElements(selectResolution);
            removeElements(selectPublish);
            removeElements(selectPlugins);

            fillVirtualReposSelect(selectResolution, response.virtualRepositories);
            fillSelect(selectPublish, response.repositories);
            fillStagingPluginsSelect(selectPlugins, response.userPlugins);
            createStagingParamsInputs(response.userPlugins);

            setSelectValue(selectResolution, oldResolutionValue);
            selectResolution.onchange();

            setSelectValue(selectPublish, oldPublishValue);
            setSelectValue(selectPlugins, oldPluginsValue);
            setStagingParamsSelectedValue(selectPlugins);

            var oldValueExistsInNewList = true;
            if (oldValueExistsInNewList) {
                oldValueExistsInNewList = compareSelectTags(selectResolution, oldSelectResolution);
            }
            if (oldValueExistsInNewList) {
                oldValueExistsInNewList = compareSelectTags(selectPublish, oldSelectPublish);
            }
            if (oldValueExistsInNewList) {
                oldValueExistsInNewList = compareSelectTags(selectPlugins, oldSelectPlugins);
            }

            if (!oldValueExistsInNewList) {
                displayWarningMessage(warning);
            }
            displaySuccessMessage(spinner, target);
        }
    });
}

function artifactoryRedeployPublisher(spinner, artifactoryUrl, deployUsername, deployPassword, overridingDeployerCredentials, bind) {
    bind.refreshFromArtifactory(spinner, artifactoryUrl, deployUsername, deployPassword, overridingDeployerCredentials, function (t) {
        var target = spinner.next();
        var warning = target.next();

        var response = t.responseObject();
        if (!response.success) {
            displayErrorResponse(spinner, target, response.responseMessage);
        }
        else {
            var selectRelease = document.getElementById("publishRepositoryKey-" + artifactoryUrl);
            var selectSnapshot = document.getElementById("publishSnapshotsRepositoryKeys-" + artifactoryUrl);
            var selectPlugins = document.getElementById("customStagingConfiguration-" + artifactoryUrl);

            var oldReleaseValue = selectRelease.value;
            var oldSnapshotValue = selectSnapshot.value;
            var oldPluginsValue = selectPlugins.value;

            var oldSelectRelease = selectRelease.cloneNode(true);
            var oldSelectSnapshot = selectSnapshot.cloneNode(true);
            var oldSelectPlugins = selectPlugins.cloneNode(true);

            removeElements(selectRelease);
            removeElements(selectSnapshot);
            removeElements(selectPlugins);

            fillSelect(selectSnapshot, response.repositories);
            fillSelect(selectRelease, response.repositories);
            fillStagingPluginsSelect(selectPlugins, response.userPlugins);
            createStagingParamsInputs(response.userPlugins);

            setSelectValue(selectRelease, oldReleaseValue);
            setSelectValue(selectSnapshot, oldSnapshotValue);
            setSelectValue(selectPlugins, oldPluginsValue);
            setStagingParamsSelectedValue(selectPlugins);

            var oldValueExistsInNewList = true;
            if (oldValueExistsInNewList) {
                oldValueExistsInNewList = compareSelectTags(selectRelease, oldSelectRelease);
            }
            if (oldValueExistsInNewList) {
                oldValueExistsInNewList = compareSelectTags(selectSnapshot, oldSelectSnapshot);
            }
            if (oldValueExistsInNewList) {
                oldValueExistsInNewList = compareSelectTags(selectPlugins, oldSelectPlugins);
            }

            if (!oldValueExistsInNewList) {
                displayWarningMessage(warning);
            }
            displaySuccessMessage(spinner, target);
        }
    });
}

function artifactoryIvyConfigurator(spinner, artifactoryUrl, deployUsername, deployPassword, overridingDeployerCredentials, bind) {
    bind.refreshFromArtifactory(spinner, artifactoryUrl, deployUsername, deployPassword, overridingDeployerCredentials, function (t) {
        var target = spinner.next();
        var warning = target.next();

        var response = t.responseObject();
        if (!response.success) {
            displayErrorResponse(spinner, target, response.responseMessage);
        }
        else {
            var select = document.getElementById("publishRepositoryKey-" + artifactoryUrl);
            var oldValue = select.value;
            var oldSelect = select.cloneNode(true);
            removeElements(select);
            fillSelect(select, response.repositories);
            setSelectValue(select, oldValue);

            var oldValueExistsInNewList = true;
            if (oldValueExistsInNewList) {
                oldValueExistsInNewList = compareSelectTags(select, oldSelect);
            }

            if (!oldValueExistsInNewList) {
                displayWarningMessage(warning);
            }
            displaySuccessMessage(spinner, target);
        }
    });
}

function fillSelect(select, list) {
    for(var i=0; i < list.length; i++) {
        var item = list[i];
        var option = document.createElement("option");
        option.text = item;
        option.innerText = item;
        option.value = item;
        select.appendChild(option);
    }
}

function fillVirtualReposSelect(select, list) {
    for(var i=0; i < list.length; i++) {
        var item = list[i];
        var option = document.createElement("option");
        option.text = item.displayName;
        option.innerText = item.displayName;
        option.value = item.value;
        select.appendChild(option);
    }
}

function fillStagingPluginsSelect(select, list) {
    for(var i=0; i < list.length; i++) {
        var item = list[i];
        var option = document.createElement("option");
        option.text = item.pluginName;
        option.innerText = item.pluginName;
        option.value = item.pluginName;
        select.appendChild(option);
    }
}

function createStagingParamsInputs(list) {
    var str = "";
    for (var i = 0; i < list.length; i++) {
        var item = list[i];
        str += "<input class='setting-input' style='display:none' id='stagingParams-" + item.pluginName + "' type='text' value='" + item.paramsString + "' />";
    }

    document.getElementById("stagingParamsDiv").innerHTML = str;
}

function setStagingParamsSelectedValue(select) {
    for(var i=0; i < select.options.length; i++) {
        var display = (i == select.selectedIndex) ? "" : "none";
        var inputName = "stagingParams-" + select.options[i].value;
        var input = document.getElementById(inputName);
        input.style.display = display;
        if (display == "") {
            input.setAttribute("name", "userPluginParams");
        } else {
            input.setAttribute("name", "");
        }

        if (display == "") {
            var div = document.getElementById("stagingParamsDiv");
            if (input.value == "") {
                div.style.display = "none";
            } else {
                div.style.display = "";
            }
        }
    }
}

function displaySuccessMessage(spinner, target) {
    spinner.style.display = "none";
    target.innerHTML = "Items refreshed successfully";
    target.removeClassName('error');
    target.style.color = "green";
}

function displayErrorResponse(spinner, target, message) {
    spinner.style.display = "none";
    target.innerHTML = message;
    target.addClassName('error');
    target.style.color = "red";
}

function displayWarningMessage(warning) {
    warning.innerHTML = "Warning! One of your previously configured items does not exist.";
    warning.style.color = "orange"
}

function removeElements(e) {
    while (e.firstChild) {
        e.removeChild(e.firstChild);
    }
}

function setSelectValue(select, value) {
    for(var i=0; i < select.options.length; i++) {
        if(select.options[i].value == value) {
            select.selectedIndex = i;
            return;
        }
    }
}

function compareSelectTags(newRepos, oldRepos) {
    if (oldRepos.options.length == 0) {
        return true;
    }

    for(var i=0; i < oldRepos.length; i++) {
        var itemOld = oldRepos[i].value;

        var flag = false;
        for(var j=0; j < newRepos.length; j++) {
            itemNew = newRepos[j].value;
            if (itemNew.value === itemOld.value) {
                flag = true;
                break;
            }
        }
        if (!flag) {
            return false;
        }
    }
    return true;
}