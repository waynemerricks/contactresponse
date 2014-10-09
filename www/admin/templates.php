<?php

  include('../includes/session.php'); //Include SESSION stuff
  include('../includes/database.php'); //Include DB stuff

  $mysqli = getDatabaseRead(); //Get MySQL connection for page

  include('../includes/loggedIn.php'); //Include logged in check
  include('../includes/user.php'); //Functions relating to logged in user

  if(!canEditTemplates())//If we don't have permission to be here, go away
  	header('Location: ../index.php');
  
  include('includes/templates.php');
  
  $overview = getOverview(14, $mysqli);
  $templates = getTemplates($mysqli);
  $languages = getLanguages($mysqli);
  
?>
<html>
  <head>
    <meta charset="utf8">
    <title>CRS | Admin - Templates</title>
    <link rel="stylesheet" type="text/css" href="../css/crs.css">
    <link rel="stylesheet" type="text/css" href="../css/menu.css">
    <link rel="stylesheet" type="text/css" href="../includes/jquery-ui.min.css">
    <script type="text/javascript" src="../includes/jquery-2.1.1.min.js"></script>
    <script type="text/javascript" src="../includes/jquery-ui.min.js"></script>
    <script type="text/javascript">

    //JQuery for datepicker
    $(document).ready(function(){
      $(".date").each(function(){
        $(this).datepicker({
          dateFormat: 'yy-mm-dd'
        });
      });
    });
  </script>
  </head>
  <body>
    <div id="template_select">
      <form action="templates.php" method="post">
        <label for="date">Select Date:</label>
        <input class="date" id="date" name="date" type="text" value="" />
        <label for="template">Template:</label>
        <select id="template" name="template">
          <option value="All">All</option>
          <?php foreach($templates as $label => $id){ ?>
            <option value="<?php echo $id . '-' . $label; ?>"><?php echo $label; ?></option>
          <?php }?>
        </select>
        <label for="templatetype">Type:</label>
        <select id="templatetype" name="templatetype">
          <option value="All">All</option>
          <option value="Email">Email</option>
          <option value="SMS">SMS</option>
        </select>
        <input id="submit" name="submit" type="submit" value="Edit"/>
      </form>
    </div>
    <div class="inbox">
      <table id="inbox" class="inbox">
        <tr class="header">
          <th rowspan="2" class="centre">Date</th>
          <?php 
          
          	//Write out the template labels
          	$default = $overview['default'];
          	
          	foreach($default as $label => $dataArray){ ?>
          			
          		<th colspan="4"><?php echo $label; ?></th>
          		
          	<?php } ?>
        </tr>
        <tr>
          <?php
        
            //Write out email/sms for each template type
            for($i = 0; $i < sizeof($templates); $i++) { ?>
          
              <th colspan="2">Email</th>
              <th colspan="2">SMS</th>
              
            <?php } ?>
        </tr>    
        <?php 
        
          writeOutStatus($default, 'Default');
          
          foreach($overview as $key => $data){
          	
          	if($key != 'default')
            	writeOutStatus($data, $key, true);
          	
          }
        
        ?>
      </table>
    </div>
    <div id="edittemplate">
      <?php 
      
        $templateLabel = 'Default Template';
        
        if(isset($_POST['date']) && strlen($_POST['date']) > 0){ 
        
        	$date = DateTime::createFromFormat('Y-m-d', $_POST['date']);
        	
        	if($date === FALSE){
        		
        		$templateLabel = 'Error with date format, please check and try again';
        		
        	}else 
        		$templateLabel = 'Template for the ' . date('jS F', $date->getTimestamp());
        		
        }
        
      ?>
      <p><strong><?php echo $templateLabel; ?></strong></p>
      
      <?php 
      
        $templateLoop = $templates;
        
      	if(isset($_POST['template']) && $_POST['template'] != 'All'){
      		
      		$templateLoop[$_POST['template']] = $templates[$_POST['template']];
      		
      	}
      	
      	$types = array();
      	$types[] = 'email';
      	$types[] = 'sms';
      	
      	if(isset($_POST['templatetype'])  && $_POST['templatetype'] != 'All'){
      		
      		$types = array();
      		$types[] = lcase($_POST['templatetype']); 
      		
      	}
      	
      	if(isset($_POST['date']) && strlen($_POST['date']) > 0){
      		
      		
      		
      	}
      	
      	foreach($templateLoop as $label => $id){
      		
      		for($i = 0; $i < sizeof($languages); $i++)
      			for($j = 0; $j < sizeof($types); $j++){
      			
      				$editTemplate = getEditTemplates($id, $types[$j], $languages[$i], $date);
      				
      				//TODO display this
      				echo $editTemplate['content']['body'] . '<br><br>';
      				
      			}
      		
      	}
	      	
      	
      
      ?>

    </div>
    <div id="spacer"></div>
    <div id="footer">
      <ul id="footer_menu"><!-- Menu Root -->
        <li class="buttons homeButton"><a href="../main.php"></a></li>
        <li class="buttons contactButton"><a href="contacts.php"></a></li>
        <li><a href="#"><?php echo getUsersName(); ?></a>
        <?php if(isAdmin()){ ?>
          <li><a href="#">Admin</a>
            <ul class="dropup">
              <?php if(canCreateUsers()){ ?>
                <li><a href="admin/createUser.php">Create User</a></li>
              <?php } ?>
              <?php if(canEditPermissions()){ ?>
                <li><a href="#">Permissions</a></li>
              <?php } ?>
              <?php if(canEditRoles()){ ?>
                <li><a href="#">Roles</a></li>
              <?php } ?>
              <?php if(canEditTemplates()){ ?>
                <li><a href="templates.php">Templates</a></li>
              <?php } ?>
            </ul>
          </li>
        <?php } ?>
        <li class="right"><a href="logout.php">Logout</a></li>
      </ul>
    </div>
  </body>
</html>
<?php

  $mysqli->close();//Close MySQL connection

?>