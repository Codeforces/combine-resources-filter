call mvn validate --batch-mode
call mvn clean source:jar javadoc:jar repository:bundle-create install -DcreateChecksum=true --batch-mode
