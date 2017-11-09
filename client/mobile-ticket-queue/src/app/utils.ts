export function formatCurrentMoment(withSeconds: boolean = false) {
  const d = new Date();
  const datestring = ("0" + d.getDate()).slice(-2) + "-" + ("0"+(d.getMonth()+1)).slice(-2) + "-" +
  d.getFullYear() + " " + ("0" + d.getHours()).slice(-2) + ":" + ("0" + d.getMinutes()).slice(-2);
  if (withSeconds) {
    return datestring + ":" + ("0" + d.getSeconds()).slice(-2);
  }
  return datestring;
}