ENQUERY_HOME="../.."
java -Djava.library.path=$ENQUERY_HOME/lib/native/ -cp $ENQUERY_HOME/encryptedquery-1.0.0-SNAPSHOT-exe.jar org.enquery.encryptedquery.responder.wideskies.ResponderDriver \
 -d base -ds dataschema.xml -i datafile.json \
 -p standalone -qs queryschema.xml -q demographic-query -o demographic-query-result


