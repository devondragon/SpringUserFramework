function validate() {
	if (document.f.username.value == "" && document.f.password.value == "") {
		alert("${noUser} and ${noPass}");
		document.f.username.focus();
		return false;
	}
	if (document.f.username.value == "") {
		alert("${noUser}");
		document.f.username.focus();
		return false;
	}
	if (document.f.password.value == "") {
		alert("${noPass}");
		document.f.password.focus();
		return false;
	}
}