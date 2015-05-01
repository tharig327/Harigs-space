/* This script and many more are available free online at
The JavaScript Source!! http://www.javascriptsource.com
Created by: Anonymous | http://musikimiz.googlepages.comLicensed under: Creative Commons License
 */

function pixTimeChange() {
  var t=new Date();
  var h = t.getHours();
  var r1="pic1.gif";
  var r2="pic2.gif";
  var el=document.getElementById('myimage');

  // See the time below. Note: The time is in 24 hour format.
  // In the example here, "7" = 7 AM; "17" =5PM.
  el.src = (h>=7 && h<17) ? r1 : r2;
}

// Multiple onload function created by: Simon Willison
// http://simonwillison.net/2004/May/26/addLoadEvent/
function addLoadEvent(func) {
  var oldonload = window.onload;
  if (typeof window.onload != 'function') {
    window.onload = func;
  } else {
    window.onload = function() {
      if (oldonload) {
        oldonload();
      }
      func();
    }
  }
}

addLoadEvent(function() {
  pixTimeChange();
});

