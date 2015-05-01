/* This script and many more are available free online at
The JavaScript Source!! http://www.javascriptsource.com
Created by: Anonymous | http://musikimiz.googlepages.comLicensed under: Creative Commons License
 */

function pixTimeChange() {
  var t=new Date();
  var h = t.getHours();
  var r1="data:image/jpeg;base64,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";
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

