/**
 * Push to Bintray helper JS functions
 *
 * Created by Aviad Shikloshi on 30/03/2015.
 */

function readOnlyMode() {
    var option = signMethodSelect.options[signMethodSelect.selectedIndex];
    if (option.value == 'false') {
        gpgPassphrase.readOnly = true;
        gpgPassphrase.style.background = '#F0F0F0';
        gpgPassphrase.value = '';
    } else {
        gpgPassphrase.readOnly = false;
        gpgPassphrase.style.background = '#FFFFFF';
    }
}

function updateSelect() {
    updateSelectWithFlag(this.checked);
}

function updateSelectWithFlag(shouldRemove) {
    if (shouldRemove) {
        removeOptionByVal('descriptor');
    } else {
        createOptionAndAppend('descriptor', 'According to descriptor file');
    }
}

function createOptionAndAppend(value, text) {
    var option = document.createElement('option');
    option.value = value;
    option.innerHTML = text;
    signMethodSelect.appendChild(option);
}

function removeOptionByVal(value) {
    var option, i;
    for (i = 0; i < signMethodSelect.length; i++) {
        option = signMethodSelect[i];
        if (option.value == value) {
            signMethodSelect.removeChild(option)
        }
    }
}

// globals
var signMethodSelect = document.getElementById('gpgSign'),
    gpgPassphrase = document.getElementById('gpgPassphrase'),
    checkbox = document.getElementsByName('override')[0];

// on document load
if (checkbox.onchange != updateSelect) {
    checkbox.addEventListener('change', updateSelect, false);
}
updateSelectWithFlag(checkbox.checked);
if (!checkbox.checked) {
    signMethodSelect.selectedIndex = 2;
}