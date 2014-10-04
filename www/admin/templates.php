<?php

  include('../includes/session.php'); //Include SESSION stuff
  include('../includes/database.php'); //Include DB stuff

  $mysqli = getDatabaseRead(); //Get MySQL connection for page

  include('../includes/loggedIn.php'); //Include logged in check
  include('../includes/user.php'); //Functions relating to logged in user

  if(!canEditTemplates())//If we don't have permission to be here, go away
  	header('Location: ../index.php');
  
  include('includes/templates.php');
  
  $overview = getOverview(14);
  
?>
<html>
  <head>
    <meta charset="utf8">
    <title>CRS | Admin - Templates</title>
    <link rel="stylesheet" type="text/css" href="css/crs.css">
    <link rel="stylesheet" type="text/css" href="css/menu.css">
  </head>
  <body>
    <div class="inbox">
      <table id="inbox" class="inbox">
        <tr class="header">
          <th class="centre">Date</th>
          <?php 
          
          	//Write out the template labels
          	$default = $overview['default'];
          	
          	$templates = 0;
          	
          	foreach($default as $label => $dataArray){ 
          	
          		$templates++; ?>
          			
          		<th colspan="4"><?php echo $label; ?></th>
          		
          	<?php } ?>
        </tr>
        <tr>
          <th>&nbsp;</th>
          <?php
        
            //Write out email/sms for each template type
            for($i = 0; $i < $templates; $i++) { ?>
          
              <th>Email</th>
              <th>SMS</th>
              
            <?php } ?>
        </tr>    
        <tr>
          <td>Default</td>
          
          <?php 
          
          	//Get true/false for each template --> language entry
            foreach($default as $key => $templatedata){
          	
            	for($i = 0; $i < sizeof($templatedata); $i++){

            		?>
            		
            		<td class="<?php echo getStatusClass($templatedata[$i]); ?>">&nbsp;</td>
            		
            		<?php 
            		
            	}
            	
            }
          	//TODO need to rework this for more than just default
          ?>
          
        </tr>
      </table>
    </div>
    <div id="spacer"></div>
    <div id="footer">
      <ul id="footer_menu"><!-- Menu Root -->
        <li class="buttons homeButton"><a href="main.php"></a></li>
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
                <li><a href="admin/templates.php">Templates</a></li>
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