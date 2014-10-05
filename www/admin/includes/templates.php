<?php 

  /**
   * Gets the status of templates for the next x days
   * @param $days number of days to get
   * @return array of true/false values as follows:
   *   [default] => [template1]
   *   					[language1] = true/false
   *   					[language2] = true/false
   *   				[template2]
   *   					[language1] = true/false
   *   					[language2] = true/false
   *   [today as Ymd] => [template1] ...
   *   [today + 1 as Ymd] => [template1] ...
   *   ...
   *   [today + $days as Ymd] => [template1] ... 
   */
  function getOverview($days){
  	
  	$mysqli = getDatabaseRead();
  	
  	/** LANGUAGES **/
  	//Read in the languages we need to look up for the templates
  	$sql = 'SELECT LOWER(`language`) AS `language` FROM `languages` WHERE 
  		`mappedTo` = 0';
  	$languages = array();
  	
  	$result = $mysqli->query($sql);

    while($row = $result->fetch_assoc())
		$languages[] = $row['language'];  	
  	
    $result->close();
    
    /** TEMPLATES **/
    //Read in the templates we'll be looking for
    $sql = 'SELECT `label`, `id` FROM `templates` ORDER BY `label` ASC';
    $templates = array();
    
    $result = $mysqli->query($sql);
    
    while($row = $result->fetch_assoc())
    	$templates[$row['label']] = $row['id'];
     
    $result->close();
    
    /** GET TEMPLATES FOR DATES **/ 
    //Get the default templates first
    $templateStatus = array();
    $default = array();
    
    foreach ($templates as $id => $label){
    	
    	for($i = 0; $i < sizeof($languages); $i++){
    		
    		$default[$label][$languages[$i]] = checkTemplateExists($id, 'email', $languages[$i], '');
    		$default[$label][$languages[$i]] = checkTemplateExists($id, 'sms', $languages[$i], '');
    		
    	}
    	
    }
    
    $templateStatus[] = $default;
    
    //Loop through each day and grab templates as necessary
    for($i = 0; $i <= $days; $i++){
    	
    	$templateForDay = array();
    	
    	foreach ($templates as $id => $label){
    		 
    		for($j = 0; $j < sizeof($languages); $j++){
    	
    			$date = '';
    			
    			if($i == 0)
    				$date = date('Ymd');
    			else
    				$date = date('Ymd', strtotime(date('Ymd') . ' + ' . $i . ' days'));
    				
    			$templateForDay[$label][$languages[$j]] = checkTemplateExists($id, 'email', $languages[$j], $date);
    			$templateForDay[$label][$languages[$j]] = checkTemplateExists($id, 'sms', $languages[$j], $date);
    	
    		}
    		 
    	}
    	
    	$templateStatus[] = $templateForDay;
    	
    }
    
    return $templateStatus;
  	
  }
  
  /**
   * checks to see if the given template exists
   */
  function checkTemplateExists($id, $type, $language, $date){
  	
  	/* Template file name format
	 * [date-]templateid_type[_language]
	 * 3_email
	 * 3_email_hindi
	 * 20141231-3_email
	 * 20141231-3_email_hindi
	 */
  	$templateFile = "$id_$type";
  	
  	if(strlen($language) > 0)
  		$templateFile .= "_$language";
  	
  	if(strlen($date) > 0)
  		$templateFile = "$date-$templateFile";
  	
  	return is_file('../../templates/' . $templateFile);
  	
  }
  
  /**
   * If exists class = green
   * If not exists and > 7 days away white
   * If not exists and <= 7 orange
   * If not exists and < 2 red 
   * @param boolean $exists
   * @param string $date
   * @return string
   */
  function getStatusClass($exists, $date = null){
  	
  	$class = 'white';
  	
  	if($exists)
  		$class = 'green';
  	else if($date == null)
  		$class = 'red';
  	else{

  		$today = new DateTime(date('Y-m-d'));
  		$statusDate = new DateTime(strtotime($date));
  		$difference = $today->diff($statusDate);
  		
  		$interval = $datetime1->diff($datetime2);
  		$days = $interval->format('%r%a');
  		
  		if($days <= 2)
  			$class = 'red';
  		else if($days <= 7)
  			$class = 'orange';
  		
  	}
  	
  	return $class;
  	
  }

?>