<?php

class Contact{

  public $name, $gender, $language, $email, $phone, $photo, $assignedUser, $created,
            $updated, $id;
  private $mysqli;
  private $customFields = array();

  function __construct($id, $mysqli){

    $this->mysqli = $mysqli;//Store DB connection for later
    $this->id = $id;

    $sql = 'SELECT * FROM `contacts` WHERE `id` = ' . $id;

    $result = $mysqli->query($sql);

    while($row = $result->fetch_assoc()){

      $this->name = $row['name'];
      $this->gender = 'Unknown';

      if($row['gender'] == 'M')
        $this->gender = 'Male';
      elseif($row['gender'] == 'F')
        $this->gender = 'Female';

      $this->language = $this->getLanguage($row['language_id']);
      $this->email = $row['email'];
      $this->phone = $row['phone'];
      $this->photo = 'N';

      if($row['photo'] == 'Y')
        $this->photo = 'Y';

      $this->assignedUser = $row['assigned_user_id'];
      $this->created = $row['created'];
      $this->updated = $row['updated'];

    }

    $this->getCustomFields();

    $result->close();

  }

  /**
   * Grabs all the custom fields associated with this record in the database
   */
  private function getCustomFields(){

    $sql = 'SELECT `id`, `type` FROM `contact_fields`';//Get the list of custom fields

    $result = $this->mysqli->query($sql);

    while($row = $result->fetch_assoc()){

      $field = $this->getField($row['type'], $row['id']);

      foreach($field as $label => $value)
        $this->customFields[$label] = $value;//value will be an array

    }

    $result->close();

  }

  /**
   * Gets the value of the given field for this objects Contact
   * @param $type s (Small) / m (Medium) / l (Large)
   * @param $fieldID ID Of the field to get
   */
  private function getField($type, $fieldID){

    $field = array();
    $table = 'contact_values_';

    if($type == 's')
      $table .= 'small';
    else if($type == 'm')
      $table .= 'medium';
    else
      $table .= 'large';

    $sql = 'SELECT `label`, `value`
            FROM `contact_fields`
            INNER JOIN
              `' . $table . '` ON `contact_fields`.`id` = `' . $table . '`.`field_id`
            WHERE `' . $table . '`.`owner_id` = ' . $this->id;

    $result = $this->mysqli->query($sql);

    while($row = $result->fetch_assoc())
      $field[$row['label']][] = $row['value'];//Store as label => array() so we can have
      //multiples e.g. alternate phone => array(number 1, number 2, etc);

    $result->close();

    return $field;

  }

  /**
   * Returns the location of this Contact object
   * @return Short location e.g. England or Birmingham, England
   */
  public function getLocation(){

    $location = 'Unknown';

    if(isset($this->customFields['Location']))
      $location = $this->customFields['Location'][0];

    return $location;

  }

  /**
   * Returns the notes associated with this Contact object
   * @return blah blah notes blah blah - remember to nl2br for html format
   */
  public function getNotes(){

    $notes = '';

    if(isset($this->customFields['Notes']))
      $notes = $this->customFields['Notes'][0];

    return $notes;

  }

  /**
   * Returns the language this contact is set to
   * @return e.g. English
   */
  private function getLanguage($lang){

    $table = 'contact_values_';

    $sql = 'SELECT `language` FROM `languages` WHERE `id` = ' . $lang;

    $result = $this->mysqli->query($sql);

    while($row = $result->fetch_assoc())
      $lang = $row['language'];

    $result->close();

    return $lang;

  }

}

?>