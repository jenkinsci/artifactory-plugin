/**
 * @author Lior Hasson
 */

function repos(button, jsFunction, artifactoryUrl, credentialsInput, bind) {

    var username;
    var password;
    var credentialsId;
    var legacyInput;
    var credentialsPluginInput;
    var overrideCredentials;

    button = button._button;
    var spinner = $(button).up("DIV").next();
    spinner.style.display = "block";
    var target = spinner.next();
    target.innerHTML = "";
    var warning = target.next();
    warning.innerHTML = "";

    legacyInput = $('legacy' + credentialsInput);
    if (legacyInput) {
        overrideCredentials = legacyInput.down('input[type=checkbox]').checked;
        username = legacyInput.down('input[type=text]').value;
        password = legacyInput.down('input[type=password]').value;
    }
    credentialsPluginInput = $(credentialsInput);
    if (credentialsPluginInput) {
        credentialsId = $(credentialsInput).down('select').value;
    }

    if (jsFunction) {
        jsFunctionsMap[jsFunction](spinner, $(artifactoryUrl).value, credentialsId, username, password, overrideCredentials, bind);
    }
}

// maps a function name to the function object
var jsFunctionsMap = {
    artifactoryIvyFreeStyleConfigurator: artifactoryIvyFreeStyleConfigurator,
    artifactoryGenericConfigurator: artifactoryGenericConfigurator,
    artifactoryMaven3NativeConfigurator: artifactoryMaven3NativeConfigurator,
    artifactoryMaven3Configurator: artifactoryMaven3Configurator,
    artifactoryGradleConfigurator: artifactoryGradleConfigurator,
    artifactoryGradleConfigurationResolve: artifactoryGradleConfigurationResolve,
    artifactoryRedeployPublisher: artifactoryRedeployPublisher,
    artifactoryIvyConfigurator: artifactoryIvyConfigurator,
    artifactoryGenericConfigurationResolve: artifactoryGenericConfigurationResolve
};


function artifactoryIvyFreeStyleConfigurator(spinner, artifactoryUrl, credentialsId, username, password, overrideCredentials, bind) {
    bind.refreshFromArtifactory(spinner, artifactoryUrl, credentialsId, username, password, overrideCredentials, function (t) {
        var target = spinner.next();
        var warning = target.next();

        var response = t.responseObject();
        if (!response.success) {
            displayErrorResponse(spinner, target, response.responseMessage);
        }
        else {
            var select = document.getElementById("select_ivyFreeRepositoryKeys-" + artifactoryUrl);
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

function artifactoryGenericConfigurator(spinner, artifactoryUrl, credentialsId, username, password, overrideCredentials, bind) {
    bind.refreshFromArtifactory(spinner, artifactoryUrl, credentialsId, username, password, overrideCredentials, function (t) {
        var target = spinner.next();
        var warning = target.next();

        var response = t.responseObject();
        if (!response.success) {
            displayErrorResponse(spinner, target, response.responseMessage);
        }
        else {
            var select = document.getElementById("select_genericRepositoryKeys-" + artifactoryUrl);
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

function artifactoryGenericConfigurationResolve(spinner, artifactoryUrl, credentialsId, username, password, overrideCredentials, bind) {
    bind.refreshResolversFromArtifactory(spinner, artifactoryUrl, credentialsId, username, password, overrideCredentials, function (t) {
        var target = spinner.next();
        var warning = target.next();

        var response = t.responseObject();
        if (!response.success) {
            displayErrorResponse(spinner, target, response.responseMessage);
        } else {
            var select = document.getElementById('select_genericResolveRepositoryKeys-' + artifactoryUrl);
            var oldValue = select.value;
            var oldSelect = select.cloneNode(true);

            removeElements(select);

            fillVirtualReposSelect(select, response.virtualRepositories);
            setSelectValue(select, oldValue);

            var oldValueExistsInNewList = compareSelectTags(select, oldSelect);
            if (!oldValueExistsInNewList) {
                displayWarningMessage(warning);
            }
            displaySuccessMessage(spinner, target);
        }
    });
}

function artifactoryMaven3NativeConfigurator(spinner, artifactoryUrl, credentialsId, username, password, overrideCredentials, bind) {
    bind.refreshResolversFromArtifactory(spinner, artifactoryUrl, credentialsId, username, password, overrideCredentials, function (t) {
        var target = spinner.next();
        var warning = target.next();

        var response = t.responseObject();
        if (!response.success) {
            displayErrorResponse(spinner, target, response.responseMessage);
        }
        else {
            var selectRelease = document.getElementById("select_maven3NativeReleaseRepositoryKeys-" + artifactoryUrl);
            var selectSnapshot = document.getElementById("select_maven3NativeSnapshotRepositoryKeys-" + artifactoryUrl);

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

function artifactoryMaven3Configurator(spinner, artifactoryUrl, credentialsId, username, password, overrideCredentials, bind) {
    bind.refreshFromArtifactory(spinner, artifactoryUrl, credentialsId, username, password, overrideCredentials, function (t) {
        var target = spinner.next();
        var warning = target.next();

        var response = t.responseObject();
        if (!response.success) {
            displayErrorResponse(spinner, target, response.responseMessage);
        }
        else {
            var selectRelease = document.getElementById("select_maven3RepositoryKeys-" + artifactoryUrl);
            var selectSnapshot = document.getElementById("select_maven3SnapshotsRepositoryKeys-" + artifactoryUrl);

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

function artifactoryGradleConfigurator(spinner, artifactoryUrl, credentialsId, username, password, overrideCredentials, bind) {
    bind.refreshFromArtifactory(spinner, artifactoryUrl, credentialsId, username, password, overrideCredentials, function (t) {
        var target = spinner.next();
        var warning = target.next();

        var response = t.responseObject();
        if (!response.success) {
            displayErrorResponse(spinner, target, response.responseMessage);
        }
        else {
            var selectPublish = document.getElementById("select_gradlePublishRepositoryKeys-" + artifactoryUrl);
            var selectPlugins = document.getElementById("gradleCustomStagingConfiguration-" + artifactoryUrl);

            var oldPublishValue = selectPublish.value;
            var oldPluginsValue = selectPlugins.value;

            var oldSelectPublish = selectPublish.cloneNode(true);
            var oldSelectPlugins = selectPlugins.cloneNode(true);

            removeElements(selectPublish);
            removeElements(selectPlugins);

            fillSelect(selectPublish, response.repositories);
            fillStagingPluginsSelect(selectPlugins, response.userPlugins);
            createStagingParamsInputs(response.userPlugins);

            setSelectValue(selectPublish, oldPublishValue);
            setSelectValue(selectPlugins, oldPluginsValue);
            setStagingParamsSelectedValue(selectPlugins);

            var oldValueExistsInNewList = true;
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

function artifactoryGradleConfigurationResolve(spinner, artifactoryUrl, credentialsId, username, password, overrideCredentials, bind) {
    bind.refreshResolversFromArtifactory(spinner, artifactoryUrl, credentialsId, username, password, overrideCredentials, function (t) {
        var target = spinner.next();
        var warning = target.next();

        var response = t.responseObject();
        if (!response.success) {
            displayErrorResponse(spinner, target, response.responseMessage);
        }
        else {
            var selectResolution = document.getElementById("select_gradleResolutionRepositoryKeys-" + artifactoryUrl);
            var oldResolutionValue = selectResolution.value;
            var oldSelectResolution = selectResolution.cloneNode(true);
            removeElements(selectResolution);
            fillVirtualReposSelect(selectResolution, response.virtualRepositories);
            setSelectValue(selectResolution, oldResolutionValue);
            var oldValueExistsInNewList = true;
            if (oldValueExistsInNewList) {
                oldValueExistsInNewList = compareSelectTags(selectResolution, oldSelectResolution);
            }
            if (!oldValueExistsInNewList) {
                displayWarningMessage(warning);
            }
            displaySuccessMessage(spinner, target);
        }
    });
}

function artifactoryRedeployPublisher(spinner, artifactoryUrl, credentialsId, username, password, overrideCredentials, bind) {
    bind.refreshFromArtifactory(spinner, artifactoryUrl, credentialsId, username, password, overrideCredentials, function (t) {
        var target = spinner.next();
        var warning = target.next();

        var response = t.responseObject();
        if (!response.success) {
            displayErrorResponse(spinner, target, response.responseMessage);
        }
        else {
            var selectRelease = document.getElementById("select_publishRepositoryKey-" + artifactoryUrl);
            var selectSnapshot = document.getElementById("select_publishSnapshotsRepositoryKeys-" + artifactoryUrl);
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

function artifactoryIvyConfigurator(spinner, artifactoryUrl, credentialsId, username, password, overrideCredentials, bind) {
    bind.refreshFromArtifactory(spinner, artifactoryUrl, credentialsId, username, password, overrideCredentials, function (t) {
        var target = spinner.next();
        var warning = target.next();

        var response = t.responseObject();
        if (!response.success) {
            displayErrorResponse(spinner, target, response.responseMessage);
        }
        else {
            var select = document.getElementById("select_publishRepositoryKey-" + artifactoryUrl);
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
    var txtId = "txt_" + select.id;
    var txtElement = document.getElementById(txtId);
    if (list.length > 0 && txtElement != undefined && txtElement.value == "") {
        txtElement.value = list[0];
    }
    for (var i = 0; i < list.length; i++) {
        var item = list[i];
        var option = document.createElement("option");
        option.text = item.value;
        option.innerText = item.value;
        option.value = item.value;
        select.appendChild(option);
    }
}

function fillVirtualReposSelect(select, list) {
    var txtId = "txt_" + select.id;
    var txtElement = document.getElementById(txtId);
    if (list.length > 0 && txtElement != undefined && txtElement.value == "") {
        txtElement.value = list[0].value;
    }
    for (var i = 0; i < list.length; i++) {
        var item = list[i];
        var option = document.createElement("option");
        option.text = item.displayName;
        option.innerText = item.displayName;
        option.value = item.value;
        select.appendChild(option);
    }
}

function fillStagingPluginsSelect(select, list) {
    for (var i = 0; i < list.length; i++) {
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
    for (var i = 0; i < select.options.length; i++) {
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
    for (var i = 0; i < select.options.length; i++) {
        if (select.options[i].value == value) {
            select.selectedIndex = i;
            return;
        }
    }
}

function compareSelectTags(newRepos, oldRepos) {
    if (oldRepos.options.length == 0) {
        return true;
    }

    for (var i = 0; i < oldRepos.length; i++) {
        var itemOld = oldRepos[i].value;

        var flag = false;
        for (var j = 0; j < newRepos.length; j++) {
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

function onReleaseResolutionSelectChange(url, select, postfix) {
    var textFieldRelease = document.getElementById('downloadReleaseRepositoryDisplayName-' + postfix + '-' + url);
    var selectFieldRelease = select;

    var releaseDisplayName = selectFieldRelease.options[selectFieldRelease.selectedIndex].text;
    if (releaseDisplayName == undefined || releaseDisplayName == null || releaseDisplayName == '') {
        releaseDisplayName = selectFieldRelease.options[selectFieldRelease.selectedIndex].innerText;
    }

    textFieldRelease.value = releaseDisplayName;
}

function onSnapshotResolutionSelectChange(url, select, postfix) {
    var textFieldSnapshot = document.getElementById('downloadSnapshotRepositoryDisplayName-' + postfix + '-' + url);
    var selectFieldSnapshot = select;

    var snapshotDisplayName = selectFieldSnapshot.options[selectFieldSnapshot.selectedIndex].text;
    if (snapshotDisplayName == undefined || snapshotDisplayName == null || snapshotDisplayName == '') {
        snapshotDisplayName = selectFieldSnapshot.options[selectFieldSnapshot.selectedIndex].innerText;
    }
    textFieldSnapshot.value = snapshotDisplayName;
}

function afterRefreshTxtUpdate(selectId, value) {
    var txtElement = document.getElementById("txt_" + selectId);
    if (txtElement != undefined && txtElement.value == "") {
        txtElement.value = value;
    }
}

// toggle button onClick callback
function toggleTxtAndSelect(txtId, txtModeId, urlRoot) {

    var select = document.getElementById('select_' + txtId);
    var txt = document.getElementById(txtId);
    var shouldUseText = document.getElementById(txtModeId);
    var button = document.getElementById('btn_' + txtId);

    var currentValue = shouldUseText.value;

    if (currentValue == undefined || currentValue == "") {
        currentValue = false;
    }

    if (JSON.parse(currentValue)) {
        shouldUseText.value = false;
    } else {
        shouldUseText.value = true;
    }
    swapHiddenValue(txt, select, button);
}

function swapHiddenValue(txt, select, button) {
    if (txt.style.display == '') {
        txt.style.display = 'none';
        select.style.display = '';
        button.firstChild.firstChild.innerHTML = "Different Value";
    } else {
        select.style.display = 'none';
        txt.style.display = '';
        button.firstChild.firstChild.innerHTML = "Select from List";
    }
}

function updateTxtValue(txtName, txtValue) {
    var txtElement = document.getElementById(txtName);
    if (txtElement != undefined) {
        txtElement.value = txtValue;
    }
}

function initTextAndSelectOnLoad(label, txtValue, selectValue) {
    var select = document.getElementById('select_' + label);
    var txt = document.getElementById(label);
    var button = document.getElementById('btn_' + label);
    if (select != undefined && txt != undefined) {
        txt.style.display = txtValue;
        select.style.display = selectValue;
        if (button != undefined) {
            if (txtValue == '') {
                button.value = "Select from List";
            } else {
                button.value = "Different Value";
            }
        }
    }
}

