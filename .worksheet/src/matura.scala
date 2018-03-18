object matura {;import org.scalaide.worksheet.runtime.library.WorksheetSupport._; def main(args: Array[String])=$execute{;$skip(366); 
  
/*
  adam hat 2*brüder wie schwestern
  eva hat 3*brüder wie schwestern
  val adamsbrueder = adamsschwestern * 2
  val brueder = adamsbrueder + 1
  val evasbrueder = (adamsschwestern -1) * 3
*/

def verhaeltnis(schwestern: Int, brueder: Int) = {
  // 2s = b-1 && 3(s-1) = b
  2 *  schwestern     == brueder -1 &&
  3 * (schwestern -1) == brueder
};System.out.println("""verhaeltnis: (schwestern: Int, brueder: Int)Boolean""");$skip(40); 
  
val geschwistervarianten = (1 to 10);System.out.println("""geschwistervarianten  : scala.collection.immutable.Range.Inclusive = """ + $show(geschwistervarianten ));$skip(343); 
  
val kinder = geschwistervarianten
  .flatMap(brueder => geschwistervarianten.map(schwestern => (schwestern, brueder)))
 	.find{ case (schwestern, brueder) => verhaeltnis(schwestern, brueder)}
	.map{  case (schwestern, brueder) => s"""
    Schwestern: ${schwestern},
    Brueder: ${brueder},
    Total: ${brueder + schwestern}
  """}
	.get;System.out.println("""kinder  : String = """ + $show(kinder ))}

 
}
