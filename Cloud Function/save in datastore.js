/**
 * Triggered from a message on a Cloud Pub/Sub topic.
 *
 * @param {!Object} event The Cloud Functions event.
 * @param {!Function} The callback function.
 */

'use strict';

const Datastore = require('@google-cloud/datastore');

const ds = Datastore({
  projectId:"XXX-XXX-XXX"
});
const kind = 'SoundValue';


// Translates from Datastore's entity format to
// the format expected by the application.
//
// Datastore format:
//   {
//     key: [kind, id],
//     data: {
//       property: value
//     }
//   }


function toDatastore (obj, nonIndexed) {
  nonIndexed = nonIndexed || [];
  const results = [];
  Object.keys(obj).forEach((k) => {
    if (obj[k] === undefined) {
      return;
    }
    results.push({
      name: k,
      value: obj[k],
      excludeFromIndexes: nonIndexed.indexOf(k) !== -1
    });
  });
  return results;
}

exports.subscribe = function subscribe(event, callback) {
  
  console.log("event : " +  JSON.stringify(event));
  
  // The Cloud Pub/Sub Message object.
  const pubsubMessage = event.data;
  
  // We're just going to log the message to prove that
  // it worked.
  
  let buffer = {};
  
  buffer = Buffer.from(pubsubMessage.data, 'base64').toString() ;
  console.log("buffer : " + buffer);
  
  let dataArray = JSON.parse(buffer); 
  console.log("dataArray : " + JSON.stringify(dataArray));
  
  let data = dataArray.data[0]; 
  console.log("data : " + JSON.stringify(data));
  
  
  let key = ds.key(kind);
  let result = toDatastore(data, ['description']) ;
  console.log("result : " +  JSON.stringify(result));
  
  const item = {
    key: key,
    data: result
  };

  console.log("item : " +  JSON.stringify(item));
  
  ds.save(
    item,
    (err) => {
      data.id = item.key.id;
      callback(err);
    }
  );
  	
  // Don't forget to call the callback.
  callback();
};



