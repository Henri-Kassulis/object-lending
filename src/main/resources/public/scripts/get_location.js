function getLocation() {
	if (navigator.geolocation) {
		navigator.geolocation.getCurrentPosition(showPosition);
	} else {
		console.log("Geolocation is not supported by this browser.");
	}
}

function round(num) {
	var factor = 100000;
	return Math.round(num * factor) / factor;
}

function showPosition(position) {
	var latitudeInput = document.getElementById("latitude");
	var longitudeInput = document.getElementById("longitude");
	latitudeInput.value = round(position.coords.latitude);
	longitudeInput.value = round(position.coords.longitude);
}
