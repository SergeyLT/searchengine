var link = document.querySelector('a[href="#dashboard"]');

// add a click event listener to the link
link.addEventListener('click', function(event) {
  // prevent the default link behavior
  event.preventDefault();

  // reload the page
  location.reload();
});