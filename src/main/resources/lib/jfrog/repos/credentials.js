/**
 * @author Aviad Shikloshi
 */

var resolverPrefix = 'resolverCredentialsId';
var deployerPrefix = 'deployerCredentialsId';

var legacyDeployerPrefix = 'legacyDeployerCredentials';
var legacyResolverPrefix = 'legacyResolverCredentials'

function updateViewForCredentialsMethod(useLegacyCredentials) {
    toggleCredentialsPluginFromView(useLegacyCredentials);
    toggleLegacyUsernamePasswordFromView(!useLegacyCredentials);
}

function toggleCredentialsPluginFromView(hide) {
    var newDisplayStyle = hide ? 'none' : '';
    var resolverTables = getElementsWithIdPrefix(resolverPrefix);
    setNewDisplayStyle(resolverTables, newDisplayStyle);
    var deployerTables = getElementsWithIdPrefix(deployerPrefix);
    setNewDisplayStyle(deployerTables, newDisplayStyle);
}

function setNewDisplayStyle(elements, display) {
    var elementsIndex;
    for (elementsIndex = 0; elementsIndex < elements.length; elementsIndex++)
        elements[elementsIndex].style.display = display;
}

function toggleLegacyUsernamePasswordFromView(hide) {
    var newDisplayStyle = hide ? 'none' : '';
    var resolverElements = getElementsWithIdPrefix(legacyResolverPrefix);
    setNewDisplayStyle(resolverElements, newDisplayStyle);
    var deployerElements = getElementsWithIdPrefix(legacyDeployerPrefix);
    setNewDisplayStyle(deployerElements, newDisplayStyle);
}

/**
 * This will return an array of HTML elements with Id that start with prefix
 * @param prefix Id prefix to search for
 * @returns {*}
 */
function getElementsWithIdPrefix(prefix) {
    if (document.querySelectorAll) {
        return Array.prototype.slice.call(document.querySelectorAll('*[id^="' + prefix + '"]'));
    } else {
        // Old browsers support
        var elements = document.getElementsByTagName('*');
        var relevantElements = [];
        for (var i = 0; i < elements.length; i++) {
            if (element.id && element.id.indexOf(prefix) === 0) {
                relevantElements.push(element);
            }
        }
        return relevantElements;
    }
}

document.addEventListener('DOMContentLoaded', function (event) {
    var useLegacyCredentialsInput = document.getElementById('useLegacyCredentials');
    useLegacyCredentialsInput.checked = JSON.parse(useLegacyCredentialsInput.value);
    updateViewForCredentialsMethod(useLegacyCredentialsInput.checked);
});
